package app.counter.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/** Tela inicial: escolher pasta do jogo, modo, mapa, ajustes simples e JOGAR. */
public class LauncherActivity extends Activity {
	private static final int REQ_FOLDER = 1, REQ_FILES = 2;
	private static final String[] MAPS = {
		"de_dust2", "de_dust", "de_inferno", "de_nuke", "de_train", "de_aztec", "cs_assault", "cs_italy", "cs_office"
	};
	private static final String[] QUALITY = { "Leve (360p) - recomendado", "Média (480p)", "Máxima (tela cheia)" };
	private static final int[] QUALITY_H = { 360, 480, 0 };

	private SharedPreferences sp;
	private TextView folderText, permText, msg;
	private Button permBtn, playBtn;
	private RadioButton modeBots, modeMenu;
	private Spinner mapSpin, qualitySpin, fpsSpin;
	private SeekBar sensBar, botsBar;
	private TextView sensLabel, botsLabel;

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		sp = getSharedPreferences("launcher", MODE_PRIVATE);
		buildUi();
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshState();
	}

	// ---- interface ----

	private int dp(int v) {
		return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
	}

	private TextView label(String t, int sp_, boolean bold) {
		TextView v = new TextView(this);
		v.setText(t);
		v.setTextSize(sp_);
		v.setTextColor(0xFFE8EAED);
		if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		v.setPadding(0, dp(10), 0, dp(4));
		return v;
	}

	private Button button(String t, int color, View.OnClickListener l) {
		Button v = new Button(this);
		v.setText(t);
		v.setTextColor(0xFFFFFFFF);
		v.setTextSize(16);
		v.setTransformationMethod(null);
		v.setBackgroundColor(color);
		v.setOnClickListener(l);
		LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
		p.topMargin = dp(8);
		v.setLayoutParams(p);
		return v;
	}

	private void buildUi() {
		ScrollView scroll = new ScrollView(this);
		scroll.setBackgroundColor(0xFF14181D);
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(24), dp(16), dp(24), dp(24));
		scroll.addView(root);

		TextView title = label("Counter-A-Launcher", 26, true);
		title.setGravity(Gravity.CENTER);
		root.addView(title);

		permText = label("", 14, false);
		root.addView(permText);
		permBtn = button("Permitir acesso aos arquivos", 0xFFB26A00, v -> askFilesPermission());
		root.addView(permBtn);

		root.addView(label("Pasta do jogo (a que contém as pastas valve e cstrike)", 14, true));
		folderText = label("", 14, false);
		root.addView(folderText);
		root.addView(button("Escolher pasta", 0xFF37474F, v -> pickFolder()));

		root.addView(label("Como jogar", 14, true));
		RadioGroup modes = new RadioGroup(this);
		modeBots = radio("Partida rápida com bots (entra direto no mapa)");
		modeMenu = radio("Menu do jogo (servidores online, configurações)");
		modes.addView(modeBots);
		modes.addView(modeMenu);
		root.addView(modes);
		(sp.getString("mode", "bots").equals("bots") ? modeBots : modeMenu).setChecked(true);

		root.addView(label("Mapa (partida com bots)", 14, true));
		mapSpin = spinner(MAPS);
		mapSpin.setSelection(sp.getInt("map", 0));
		root.addView(mapSpin);

		botsLabel = label("", 14, false);
		root.addView(botsLabel);
		botsBar = seek(16, sp.getInt("bots", 6), (v) -> botsLabel.setText("Quantidade de bots: " + v));
		root.addView(botsBar);

		root.addView(label("Qualidade (menor = mais fluido no A11)", 14, true));
		qualitySpin = spinner(QUALITY);
		qualitySpin.setSelection(sp.getInt("quality", 0));
		root.addView(qualitySpin);

		root.addView(label("Limite de FPS", 14, true));
		fpsSpin = spinner(new String[] { "30", "45", "60" });
		fpsSpin.setSelection(sp.getInt("fps", 0));
		root.addView(fpsSpin);

		sensLabel = label("", 14, false);
		root.addView(sensLabel);
		sensBar = seek(20, sp.getInt("sens", 10), (v) -> sensLabel.setText("Sensibilidade ao mirar: " + v));
		root.addView(sensBar);

		root.addView(button("Editar botões (posição e tamanho)", 0xFF37474F,
				v -> startActivity(new Intent(this, EditorActivity.class))));

		playBtn = button("JOGAR", 0xFF2E7D32, v -> play());
		playBtn.setTextSize(22);
		((LinearLayout.LayoutParams) playBtn.getLayoutParams()).height = dp(68);
		root.addView(playBtn);

		msg = label("", 14, false);
		msg.setTextColor(0xFFFF8A80);
		root.addView(msg);

		setContentView(scroll);
	}

	private RadioButton radio(String t) {
		RadioButton r = new RadioButton(this);
		r.setText(t);
		r.setTextColor(0xFFE8EAED);
		r.setId(View.generateViewId());
		return r;
	}

	private Spinner spinner(String[] items) {
		Spinner s = new Spinner(this);
		ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
		a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		s.setAdapter(a);
		return s;
	}

	private interface IntCb { void on(int v); }

	private SeekBar seek(int max, int value, IntCb cb) {
		SeekBar s = new SeekBar(this);
		s.setMax(max);
		s.setProgress(value);
		s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override public void onProgressChanged(SeekBar sb, int p, boolean user) { cb.on(max == 20 ? Math.max(1, p) : p); }
			@Override public void onStartTrackingTouch(SeekBar sb) { }
			@Override public void onStopTrackingTouch(SeekBar sb) { }
		});
		cb.on(max == 20 ? Math.max(1, value) : value);
		return s;
	}

	// ---- estado / permissões / pasta ----

	private boolean hasFilesAccess() {
		if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
		return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
				== android.content.pm.PackageManager.PERMISSION_GRANTED;
	}

	private void askFilesPermission() {
		if (Build.VERSION.SDK_INT >= 30) {
			Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
					Uri.parse("package:" + getPackageName()));
			startActivityForResult(i, REQ_FILES);
		} else {
			requestPermissions(new String[] {
				android.Manifest.permission.READ_EXTERNAL_STORAGE,
				android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQ_FILES);
		}
	}

	private void pickFolder() {
		startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER);
	}

	@Override
	protected void onActivityResult(int req, int res, Intent data) {
		super.onActivityResult(req, res, data);
		if (req == REQ_FOLDER && res == RESULT_OK && data != null && data.getData() != null) {
			sp.edit().putString("folder", treeToPath(data.getData())).apply();
		}
		refreshState();
	}

	@Override
	public void onRequestPermissionsResult(int req, String[] p, int[] r) {
		refreshState();
	}

	private static String treeToPath(Uri uri) {
		String id = DocumentsContract.getTreeDocumentId(uri); // ex.: primary:Download/CS
		int i = id.indexOf(':');
		String vol = i < 0 ? id : id.substring(0, i);
		String rel = i < 0 ? "" : id.substring(i + 1);
		String base = vol.equals("primary") ? Environment.getExternalStorageDirectory().getPath() : "/storage/" + vol;
		return rel.isEmpty() ? base : base + "/" + rel;
	}

	/** Aceita a pasta que contém cstrike, ou a própria pasta cstrike. */
	private File gameRoot() {
		String f = sp.getString("folder", null);
		if (f == null) return null;
		File dir = new File(f);
		if (new File(dir, "cstrike").isDirectory()) return dir;
		if (dir.getName().equals("cstrike") && dir.getParentFile() != null) return dir.getParentFile();
		return dir;
	}

	private void refreshState() {
		boolean ok = hasFilesAccess();
		permText.setText(ok ? "Acesso aos arquivos: liberado" : "Acesso aos arquivos: necessário para ler a pasta do jogo");
		permBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
		File root = gameRoot();
		folderText.setText(root == null ? "Nenhuma pasta escolhida" : root.getPath());
		msg.setText("");
	}

	// ---- jogar ----

	private void play() {
		msg.setText("");
		if (!hasFilesAccess()) { msg.setText("Libere o acesso aos arquivos primeiro."); return; }
		File root = gameRoot();
		if (root == null) { msg.setText("Escolha a pasta do jogo."); return; }
		if (!new File(root, "cstrike").isDirectory()) {
			msg.setText("Não achei a pasta 'cstrike' em " + root.getPath() + ". Escolha a pasta que contém valve e cstrike.");
			return;
		}
		if (!new File(root, "valve").isDirectory()) {
			msg.setText("Não achei a pasta 'valve' em " + root.getPath() + ". O CS precisa dela junto com a cstrike.");
			return;
		}

		boolean bots = modeBots.isChecked();
		int map = mapSpin.getSelectedItemPosition(), quality = qualitySpin.getSelectedItemPosition();
		int fps = fpsSpin.getSelectedItemPosition(), sens = Math.max(1, sensBar.getProgress()), nb = botsBar.getProgress();
		sp.edit().putString("mode", bots ? "bots" : "menu").putInt("map", map).putInt("quality", quality)
				.putInt("fps", fps).putInt("sens", sens).putInt("bots", nb).apply();

		try {
			writeCfg(new File(root, "cstrike/counter.cfg"), bots, nb, Integer.parseInt((String) fpsSpin.getSelectedItem()), sens);
		} catch (IOException e) {
			msg.setText("Não consegui gravar a configuração em " + root + "/cstrike: " + e.getMessage());
			return;
		}

		StringBuilder args = new StringBuilder("-log -dll @yapb");
		int h = QUALITY_H[quality];
		if (h > 0) {
			DisplayMetrics dm = getResources().getDisplayMetrics();
			float aspect = Math.max(dm.widthPixels, dm.heightPixels) / (float) Math.min(dm.widthPixels, dm.heightPixels);
			int w = Math.round(h * aspect / 2f) * 2;
			args.append(" -width ").append(w).append(" -height ").append(h);
		}
		args.append(" +exec counter.cfg");
		if (bots) args.append(" +maxplayers 16 +map ").append(MAPS[map]);

		Intent i = new Intent(this, GameActivity.class)
				.putExtra("gamedir", "cstrike")
				.putExtra("basedir", root.getPath())
				.putExtra("gamelibdir", getApplicationInfo().nativeLibraryDir)
				.putExtra("package", getPackageName())
				.putExtra("argv", args.toString())
				.putExtra("menu_mode", !bots);
		startActivity(i);
	}

	private void writeCfg(File f, boolean bots, int nbots, int fps, int sens) throws IOException {
		StringBuilder c = new StringBuilder();
		c.append("// Gerado pelo Counter-A-Launcher a cada início. Pode apagar.\n");
		// teclas usadas pelos botões da tela
		c.append("bind F5 \"+attack\"\nbind F6 \"+jump\"\nbind F7 \"+duck\"\n");
		c.append("bind r \"+reload\"\nbind e \"+use\"\nbind TAB \"+showscores\"\nbind b \"buy\"\n");
		c.append("bind w \"+forward\"\nbind a \"+moveleft\"\nbind s \"+back\"\nbind d \"+moveright\"\n");
		for (int k = 1; k <= 5; k++) c.append("bind ").append(k).append(" \"slot").append(k).append("\"\n");
		c.append("fps_max ").append(fps).append("\n");
		float scale = sens / 10f;
		c.append("touch_pitch ").append(Math.round(90 * scale)).append("\n");
		c.append("touch_yaw ").append(Math.round(120 * scale)).append("\n");
		c.append("bind F8 \"chooseteam\"\n");
		if (bots) c.append("yb_quota ").append(nbots).append("\n");
		try (FileWriter w = new FileWriter(f, false)) { w.write(c.toString()); }

		// touch.cfg vazio: sem ele o Xash/CS carregam os botões deles por baixo dos nossos
		// e eles roubam o toque da câmera. Guardamos uma cópia do arquivo antigo uma vez.
		File touch = new File(f.getParentFile(), "touch.cfg");
		File backup = new File(f.getParentFile(), "touch.cfg.antigo");
		if (touch.exists() && !backup.exists()) {
			String old = new String(java.nio.file.Files.readAllBytes(touch.toPath()));
			if (!old.contains("Counter-A-Launcher")) touch.renameTo(backup);
		}
		try (FileWriter w = new FileWriter(touch, false)) {
			w.write("// Counter-A-Launcher: sem botoes do Xash (os botoes sao do proprio app)\n");
		}
	}
}
