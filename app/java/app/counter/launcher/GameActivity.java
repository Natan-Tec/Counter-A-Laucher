package app.counter.launcher;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
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

	}

	/**
	 * Intercepta todos os toques antes da hierarquia de views. (O SDL registra o próprio ouvinte de toque
	 * a cada retomada do jogo, então um ouvinte nosso na surface seria apagado.)
	 * Dedos nos nossos botões ficam com o app; os demais vão ao SDL (câmera e menus).
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (controls == null || mSurface == null || controls.isEditMode()) return super.dispatchTouchEvent(ev);
		int[] loc = new int[2];
		mSurface.getLocationInWindow(loc);
		ev.offsetLocation(-loc[0], -loc[1]);
		try {
			return controls.surfaceTouch(mSurface, ev, (View.OnTouchListener) mSurface);
		} catch (RuntimeException ex) {
			return super.dispatchTouchEvent(ev);
		} finally {
			ev.offsetLocation(loc[0], loc[1]);
		}
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
