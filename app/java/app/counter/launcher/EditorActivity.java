package app.counter.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** Editor de botões fora do jogo: mover, mudar tamanho e transparência. */
public class EditorActivity extends Activity {
	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		ControlsView v = new ControlsView(this);
		v.setBackgroundColor(0xFF1B1F24);
		v.setEditMode(true);
		v.setListener(this::finish);
		setContentView(v);
		v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
	}
}
