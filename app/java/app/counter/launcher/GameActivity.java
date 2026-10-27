package app.counter.launcher;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import org.libsdl.app.SDLActivity;

import su.xash.engine.XashActivity;

import java.io.File;
import java.io.FileInputStream;

/** Tela do jogo: motor Xash3D + controles do Counter-A-Launcher por cima. */
public class GameActivity extends XashActivity {
	private ControlsView controls;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private File stateFile;

	private final Runnable poll = new Runnable() {
		@Override
		public void run() {
			if (controls != null) controls.setBuyZone(readBuyZone());
			handler.postDelayed(this, 250);
		}
	};

	@Override
	protected String[] getArguments() {
		// A biblioteca do CS grava aqui quando o jogador entra/sai da zona de compra.
		nativeSetenv("COUNTER_STATE_DIR", getFilesDir().getAbsolutePath());
		// desliga os botões de toque do Xash/CS (usamos os nossos)
		nativeSetenv("COUNTER_NO_TOUCH_BUTTONS", "1");
		return super.getArguments();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		stateFile = new File(getFilesDir(), "counter_state");
		try (java.io.FileOutputStream o = new java.io.FileOutputStream(stateFile)) { o.write('0'); } catch (Exception ignored) { }

		super.onCreate(savedInstanceState);

		if (mLayout == null) return; // o SDL falhou ao iniciar e mostra o próprio erro

		controls = new ControlsView(this);
		controls.setKeySink(new ControlsView.KeySink() {
			@Override public void down(int k) { SDLActivity.onNativeKeyDown(k); }
			@Override public void up(int k) { SDLActivity.onNativeKeyUp(k); }
		});
		controls.setControlsOn(!getIntent().getBooleanExtra("menu_mode", false));
		mLayout.addView(controls, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		// Todos os toques passam por aqui: dedos nos nossos botões ficam com a gente, o resto vai para o jogo.
		final View.OnTouchListener sdlListener = (View.OnTouchListener) mSurface;
		mSurface.setOnTouchListener((v, e) -> {
			try {
				return controls.surfaceTouch(v, e, sdlListener);
			} catch (RuntimeException ex) {
				// se algo der errado nos nossos controles, o jogo ainda recebe o toque normal
				return sdlListener.onTouch(v, e);
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		handler.post(poll);
	}

	@Override
	protected void onPause() {
		handler.removeCallbacks(poll);
		if (controls != null) controls.releaseAll();
		super.onPause();
	}

	private boolean readBuyZone() {
		try (FileInputStream in = new FileInputStream(stateFile)) {
			return in.read() == '1';
		} catch (Exception e) {
			return false;
		}
	}
}
