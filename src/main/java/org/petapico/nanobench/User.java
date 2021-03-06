package org.petapico.nanobench;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.nanopub.MalformedNanopubException;
import org.nanopub.MultiNanopubRdfHandler;
import org.nanopub.Nanopub;
import org.nanopub.extra.security.IntroNanopub;
import org.nanopub.extra.security.IntroNanopub.IntroExtractor;
import org.nanopub.extra.server.FetchIndex;

public class User implements Serializable, Comparable<User> {

	private static final long serialVersionUID = 1L;

	private static ValueFactory vf = SimpleValueFactory.getInstance();

	// TODO Make this configurable:
	private static String authorityIndex = "http://purl.org/np/RAZ4KCC6EceKwJP602FI4WG0UgsjS0OaGDuKvadd6z-jI";

	private static final IRI DECLARED_BY = vf.createIRI("http://purl.org/nanopub/x/declaredBy");
	private static final IRI HAS_PUBLIC_KEY = vf.createIRI("http://purl.org/nanopub/x/hasPublicKey");

	private static List<User> approvedUsers;
	private static List<User> unapprovedUsers;
	private static Map<String,User> userIdMap;
	private static Map<String,User> userPubkeyMap;
	private static Map<String,String> nameFromOrcidMap = new HashMap<>();

	public static synchronized void refreshUsers() {
		approvedUsers = new ArrayList<>();
		unapprovedUsers = new ArrayList<>();
		userIdMap = new HashMap<String,User>();
		userPubkeyMap = new HashMap<String,User>();

		// TODO Make update strategy configurable:
		String latestAuthorityIndex = ApiAccess.getLatestVersionId(authorityIndex);
		System.err.println("Using authority index: " + latestAuthorityIndex);
		// TODO use piped out-in stream here:
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new FetchIndex(latestAuthorityIndex, out, RDFFormat.TRIG, false, true, null).run();
		InputStream in = new ByteArrayInputStream(out.toByteArray());
		try {
			MultiNanopubRdfHandler.process(RDFFormat.TRIG, in, new MultiNanopubRdfHandler.NanopubHandler() {
				@Override
				public void handleNanopub(Nanopub np) {
					createUser(np, true);
				}
			});
		} catch (RDFParseException | RDFHandlerException | IOException | MalformedNanopubException ex) {
			ex.printStackTrace();
		}
		try {
			Map<String,String> params = new HashMap<>();
			params.put("pred", "http://purl.org/nanopub/x/approves-of");
			List<ApiResponseEntry> results = ApiAccess.getAll("find_signed_nanopubs_with_pattern", params).getData();
			while (true) {
				boolean keepLooping = false;
				for (ApiResponseEntry entry : new ArrayList<>(results)) {
					if (!entry.get("superseded").equals("0") || !entry.get("retracted").equals("0")) continue;
					String subj = entry.get("subj");
					String pubkey = entry.get("pubkey");
					String obj = entry.get("obj");
					if (userPubkeyMap.containsKey(pubkey)) {
						User u = userPubkeyMap.get(pubkey);
						if (u.getId().stringValue().equals(subj)) {
							Nanopub np = Utils.getNanopub(obj);
							if (np != null) {
								User.createUser(np, true);
							} else {
								System.err.println("Failed to load user: " + obj);
							}
						}
						results.remove(entry);
						keepLooping = true;
					}
				}
				if (!keepLooping) break;
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		try {
			for (ApiResponseEntry entry : ApiAccess.getAll("get_all_users", null).getData()) {
				createUser(entry, false);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		Collections.sort(approvedUsers);
		Collections.sort(unapprovedUsers);
	}

	public static synchronized List<User> getUsers(boolean approved) {
		if (unapprovedUsers == null) {
			refreshUsers();
		}
		if (approved) {
			return approvedUsers;
		} else {
			return unapprovedUsers;
		}
	}

	public static User getUser(String id) {
		if (userIdMap == null) {
			refreshUsers();
		}
		return userIdMap.get(id);
	}

	public static User getUserForPubkey(String pubkey) {
		if (userPubkeyMap == null) {
			refreshUsers();
		}
		return userPubkeyMap.get(pubkey);
	}

	private IRI id;
	private String name;
	private IRI introNpIri;
	private String pubkeyString;

	private User(IRI id, String name, IRI introNpIri, String pubkeyString) {
		this.id = id;
		this.name = name;
		this.introNpIri = introNpIri;
		this.pubkeyString = pubkeyString;
	}

	private static void createUser(ApiResponseEntry entry, boolean approved) {
		User user = new User(vf.createIRI(entry.get("user")), entry.get("name"), vf.createIRI(entry.get("intronp")), entry.get("pubkey"));
		registerUser(user, approved);
	}

	private static void createUser(Nanopub np, boolean approved) {
		IRI userId = null;
		String publicKey = null;
		String name = null;
		for (Statement st : np.getAssertion()) {
			// TODO: Do a proper check of assertion content:
			if (st.getPredicate().equals(DECLARED_BY) && st.getObject() instanceof IRI) {
				userId = (IRI) st.getObject();
			} else if (st.getPredicate().equals(HAS_PUBLIC_KEY) && st.getObject() instanceof Literal) {
				publicKey = st.getObject().stringValue();
			} else if (st.getPredicate().equals(FOAF.NAME) && st.getObject() instanceof Literal) {
				name = st.getObject().stringValue();
			}
		}
		if (userId == null || publicKey == null) return;
		User user = new User(userId, name, np.getUri(), publicKey);
		registerUser(user, approved);
	}

	private static void registerUser(User user, boolean approved) {
		String userId = user.getId().stringValue();
		if (userId.startsWith("https://orcid.org/")) {
			// Some simple ORCID ID wellformedness check:
			if (!userId.matches("https://orcid.org/[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{3}[0-9X]")) return;
		}
		String publicKey = user.getPubkeyString();
		if (userIdMap.containsKey(userId) && !userIdMap.get(userId).getPubkeyString().equals(publicKey)) {
			//System.err.println("User ID already registered with different public key: " + userId + " | " + publicKey);
			return;
		} else if (userIdMap.containsKey(userId)) {
			//System.err.println("User ID already registered: " + userId);
			return;
		} else if (userPubkeyMap.containsKey(publicKey)) {
			//System.err.println("User public key already registered: " + publicKey);
			return;
		}
		userIdMap.put(userId, user);
		userPubkeyMap.put(publicKey, user);
		if (approved) {
			approvedUsers.add(user);
		} else {
			unapprovedUsers.add(user);
		}
	}

	public IRI getId() {
		return id;
	}

	public String getShortId() {
		return id.stringValue().replaceFirst("^https://orcid.org/", "");
	}

	public String getName() {
		return name;
	}

	public String getDisplayName() {
		if (name != null && !name.isEmpty()) {
			return name + " (" + getShortId() + ")";
		}
//		String nameFromOrcid = getNameFromOrcid(id.stringValue());
//		if (nameFromOrcid != null && !nameFromOrcid.isEmpty()) {
//			return nameFromOrcid + " (" + getShortId() + ")";
//		}
		return getShortId();
	}

	public String getShortDisplayName() {
		if (name != null && !name.isEmpty()) {
			return name;
		}
//		String nameFromOrcid = getNameFromOrcid(id.stringValue());
//		if (nameFromOrcid != null && !nameFromOrcid.isEmpty()) {
//			return nameFromOrcid;
//		}
		return getShortId();
	}

	public IRI getIntropubIri() {
		return introNpIri;
	}

	public String getPubkeyString() {
		return pubkeyString;
	}

	@Override
	public int compareTo(User other) {
		return getDisplayName().compareTo(other.getDisplayName());
	}

//	public String getNameFromOrcid(String userId) {
//		if (!nameFromOrcidMap.containsKey(userId)) {
//			try {
//				IntroExtractor ie = IntroNanopub.extract(userId, null);
//				if (ie != null) {
//					nameFromOrcidMap.put(userId, ie.getName());
//				} else {
//					nameFromOrcidMap.put(userId, null);
//				}
//			} catch (IOException ex) {
//				System.err.println("Could not get name from ORCID account: " + userId);
//			}
//		}
//		return nameFromOrcidMap.get(userId);
//	}

}
