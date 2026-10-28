package app.counter.launcher;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Procura versão nova nas Releases do GitHub, avisa, baixa e abre a instalação. */
final class Updater {
	private static final String API = "https://api.github.com/repos/Natan-Tec/counter-a-laucher/releases/latest";
	private static final String APK_NAME = "Counter-A-Launcher.apk";
	private static final String CHANNEL = "updates";

	static final class Info {
		int build;
		String apkUrl;
	}

	interface Callback {
		void onResult(Info info); // chamado na thread principal; null = sem versão nova (ou sem internet)
	}

	private Updater() { }

	/** O número do build fica no versionName ("build-12"), gravado pelo workflow. */
	static int currentBuild(Context c) {
		try {
			String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
			return Integer.parseInt(v.replaceAll("\\D+", ""));
		} catch (Exception e) {
			return 0;
		}
	}

	static void check(Context ctx, Callback cb) {
		final Context app = ctx.getApplicationContext();
		new Thread(() -> {
			Info result = null;
			try {
				HttpURLConnection con = (HttpURLConnection) new URL(API).openConnection();
				con.setConnectTimeout(8000);
				con.setReadTimeout(8000);
				con.setRequestProperty("Accept", "application/vnd.github+json");
				con.setRequestProperty("User-Agent", "Counter-A-Launcher");
				if (con.getResponseCode() == 200) {
					StringBuilder sb = new StringBuilder();
					try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
						String line;
						while ((line = r.readLine()) != null) sb.append(line);
					}
					JSONObject j = new JSONObject(sb.toString());
					int build = Integer.parseInt(j.getString("tag_name").replaceAll("\\D+", ""));
					JSONArray assets = j.getJSONArray("assets");
					String url = null;
					for (int i = 0; i < assets.length(); i++) {
						JSONObject a = assets.getJSONObject(i);
						if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break; }
					}
					if (url != null && build > currentBuild(app)) {
						result = new Info();
						result.build = build;
						result.apkUrl = url;
					}
				}
				con.disconnect();
			} catch (Exception ignored) {
				// sem internet ou resposta inesperada: simplesmente não mostra nada
			}
			final Info r = result;
			new Handler(Looper.getMainLooper()).post(() -> cb.onResult(r));
		}).start();
	}

	/** Notificação "Nova versão disponível" (toque abre o app). */
	static void notifyUpdate(Context ctx, Info info) {
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		if (nm == null) return;
		if (Build.VERSION.SDK_INT >= 26) {
			nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT));
		}
		PendingIntent pi = PendingIntent.getActivity(ctx, 0, new Intent(ctx, LauncherActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
		android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26
				? new android.app.Notification.Builder(ctx, CHANNEL)
				: new android.app.Notification.Builder(ctx);
		b.setSmallIcon(android.R.drawable.stat_sys_download_done)
				.setContentTitle("Counter-A-Launcher: nova versão")
				.setContentText("Build " + info.build + " disponível. Toque para atualizar.")
				.setContentIntent(pi)
				.setAutoCancel(true);
		try {
			nm.notify(1, b.build());
		} catch (SecurityException ignored) { }
	}

	/** Baixa o APK; devolve o id do download. */
	static long download(Context ctx, Info info) {
		File old = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME);
		if (old.exists()) old.delete();
		DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
		DownloadManager.Request r = new DownloadManager.Request(Uri.parse(info.apkUrl));
		r.setTitle("Counter-A-Launcher build " + info.build);
		r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
		r.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, APK_NAME);
		return dm.enqueue(r);
	}

	/** Abre o instalador do Android para o APK baixado. */
	static void install(Context ctx) {
		File f = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME);
		if (!f.exists()) return;
		if (Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
			Toast.makeText(ctx, "Permita instalar apps deste app e toque em Atualizar de novo.", Toast.LENGTH_LONG).show();
			ctx.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
					Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
			return;
		}
		Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
		ctx.startActivity(new Intent(Intent.ACTION_VIEW)
				.setDataAndType(uri, "application/vnd.android.package-archive")
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
	}
}
