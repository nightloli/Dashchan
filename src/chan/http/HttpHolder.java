package chan.http;

import android.net.Uri;
import chan.annotation.Public;
import com.mishiranu.dashchan.content.model.ErrorItem;
import java.io.Closeable;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Public
public final class HttpHolder {
	public interface Use extends Closeable {
		void close();
	}

	private Thread thread;
	HttpSession session;
	private ArrayList<HttpSession> sessions;

	public HttpHolder() {}

	void checkThread() {
		synchronized (this) {
			if (thread != Thread.currentThread()) {
				throw new IllegalStateException("This action is allowed from the initial thread only");
			}
		}
	}

	public Use use() {
		// Lock for concurrent "thread" variable access
		synchronized (this) {
			if (thread != null) {
				checkThread();
				if (sessions == null) {
					sessions = new ArrayList<>();
				}
				sessions.add(session);
				if (session != null) {
					session.disconnectAndClear();
				}
				session = null;
				return () -> {
					releaseSession();
					session = sessions.remove(sessions.size() - 1);
				};
			} else {
				thread = Thread.currentThread();
				return this::releaseSession;
			}
		}
	}

	void initSession(HttpClient client, Uri uri, Proxy proxy,
			String chanName, boolean verifyCertificate, int delay, int maxAttempts) {
		checkThread();
		if (session != null) {
			session.disconnectAndClear();
		}
		session = new HttpSession(this, client, proxy, chanName, verifyCertificate, delay);
		session.requestedUri = uri;
		session.attempt = maxAttempts;
		session.forceGet = false;
	}

	private void releaseSession() {
		checkThread();
		if (session != null) {
			session.disconnectAndClear();
		}
	}

	volatile boolean interrupted = false;

	public interface Callback {
		void onDisconnectRequested();
	}

	public void interrupt() {
		interrupted = true;
	}

	// TODO CHAN
	// Remove this method after updating
	// alterchan bunbunmaru candydollchan chiochan chuckdfwk cirno diochan dobrochan kurisach nowere nulltirech owlchan
	// ponyach ponychan sevenchan shanachan sharechan taima valkyria yakujimoe
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public void disconnect() {
		checkThread();
		if (session != null) {
			session.disconnectAndClear();
		}
	}

	// TODO CHAN
	// Remove this method after updating
	// alterchan bunbunmaru candydollchan chiochan chuckdfwk cirno diochan dobrochan kurisach nowere nulltirech owlchan
	// ponyach ponychan sevenchan shanachan sharechan taima valkyria yakujimoe
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public HttpResponse read() throws HttpException {
		checkThread();
		if (session != null && session.response != null) {
			return session.response;
		}
		throw new HttpException(ErrorItem.Type.DOWNLOAD, false, false);
	}

	// TODO CHAN
	// Remove this method after updating
	// archiverbt arhivach chiochan chuckdfwk desustorage exach fiftyfive fourplebs horochan nulltirech onechanca
	// ponychan tiretirech
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public void checkResponseCode() throws HttpException {
		checkThread();
		if (session != null) {
			session.checkResponseCode();
		}
	}

	// TODO CHAN
	// Remove this method after updating
	// alphachan alterchan anonfm archiverbt arhivach bunbunmaru candydollchan chiochan chuckdfwk cirno desustorage
	// diochan dobrochan exach fiftyfive fourplebs horochan kurisach nowere nulltirech onechanca owlchan ponyach
	// ponychan sevenchan shanachan sharechan taima tiretirech valkyria yakujimoe
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public int getResponseCode() {
		checkThread();
		return session != null ? session.getResponseCode() : -1;
	}

	// TODO CHAN
	// Remove this method after updating
	// alphachan alterchan anonfm bunbunmaru candydollchan chiochan chuckdfwk cirno diochan dobrochan exach kurisach
	// nowere onechanca owlchan ponyach ponychan sharechan yakujimoe
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public Uri getRedirectedUri() {
		checkThread();
		return session != null ? session.redirectedUri : null;
	}

	// TODO CHAN
	// Remove this method after updating
	// alterchan anonfm fourchan wizardchan
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public Map<String, List<String>> getHeaderFields() {
		checkThread();
		return session != null ? session.getHeaderFields() : Collections.emptyMap();
	}

	// TODO CHAN
	// Remove this method after updating
	// alphachan alterchan arhivach chaosach chiochan chuckdfwk dobrochan dvach endchan exach haibane kurisach lolifox
	// onechanca ponyach
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public String getCookieValue(String name) {
		checkThread();
		return session != null ? session.getCookieValue(name) : null;
	}

	// TODO CHAN
	// Remove this method after updating
	// fiftyfive fourchan
	// Added: 18.10.20 19:08
	@Deprecated
	@Public
	public HttpValidator getValidator() {
		return extractValidator();
	}

	public HttpValidator extractValidator() {
		checkThread();
		return session != null && session.response != null ? session.response.getValidator() : null;
	}
}
