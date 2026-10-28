package app.counter.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Controles na tela: joystick visível, botões editáveis (posição/tamanho/transparência)
 * e botão de compra que só aparece na zona de compra.
 * Toques fora dos controles passam direto para o jogo (olhar/mirar e menus).
 */
public class ControlsView extends View {

	public interface KeySink {
		void down(int keyCode);
		void up(int keyCode);
	}

	public interface Listener {
		void onEditDone();
	}

	private static class Ctl {
		final String id, label;
		final int key;
		final float dx, dy, dsize;
		float x, y, size;
		int pointer = -1;

		Ctl(String id, String label, int key, float x, float y, float size) {
			this.id = id;
			this.label = label;
			this.key = key;
			this.dx = x;
			this.dy = y;
			this.dsize = size;
			this.x = x;
			this.y = y;
			this.size = size;
		}

		boolean isStick() { return id.equals("stick"); }
	}

	// Posição (x, y) = centro em fração da tela; tamanho = diâmetro em fração da altura.
	private final List<Ctl> ctls = new ArrayList<>();
	private final SharedPreferences sp;
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

	private KeySink sink;
	private Listener listener;
	private boolean editMode;
	private boolean buyZone;
	private boolean controlsOn = true;
	private float opacity = 0.55f;
	private Ctl selected;
	private Ctl dragging;
	private float dragOffX, dragOffY;

	// joystick
	private float knobX, knobY; // -1..1
	private boolean kF, kB, kL, kR;

	// botões de edição
	private static final int A_SMALLER = 1, A_BIGGER = 2, A_LESS = 3, A_MORE = 4, A_RESET = 5, A_SAVE = 6;
	private final List<RectF> panelRects = new ArrayList<>();
	private final List<Integer> panelActions = new ArrayList<>();
	private final List<String> panelLabels = new ArrayList<>();
	private final RectF gearRect = new RectF(), eyeRect = new RectF();
	private int gearPointer = -1;

	public ControlsView(Context c) {
		super(c);
		sp = c.getSharedPreferences("layout", Context.MODE_PRIVATE);
		ctls.add(new Ctl("stick", "", 0, 0.13f, 0.70f, 0.42f));
		ctls.add(new Ctl("fire", "FOGO", KeyEvent.KEYCODE_F5, 0.88f, 0.55f, 0.30f));
		ctls.add(new Ctl("jump", "PULAR", KeyEvent.KEYCODE_F6, 0.77f, 0.78f, 0.20f));
		ctls.add(new Ctl("duck", "AGACHAR", KeyEvent.KEYCODE_F7, 0.91f, 0.85f, 0.17f));
		ctls.add(new Ctl("reload", "RECARR.", KeyEvent.KEYCODE_R, 0.70f, 0.55f, 0.17f));
		ctls.add(new Ctl("use", "USAR", KeyEvent.KEYCODE_E, 0.78f, 0.30f, 0.15f));
		ctls.add(new Ctl("aim", "MIRA", KeyEvent.KEYCODE_F9, 0.64f, 0.33f, 0.15f));
		ctls.add(new Ctl("scores", "TAB", KeyEvent.KEYCODE_TAB, 0.93f, 0.25f, 0.12f));
		ctls.add(new Ctl("buy", "COMPRAR", KeyEvent.KEYCODE_B, 0.62f, 0.78f, 0.15f));
		ctls.add(new Ctl("esc", "MENU", KeyEvent.KEYCODE_ESCAPE, 0.95f, 0.08f, 0.11f));
		ctls.add(new Ctl("team", "TIME", KeyEvent.KEYCODE_F8, 0.86f, 0.08f, 0.11f));
		for (int i = 0; i < 5; i++) {
			ctls.add(new Ctl("slot" + (i + 1), String.valueOf(i + 1), KeyEvent.KEYCODE_1 + i,
					0.40f + 0.06f * i, 0.09f, 0.11f));
		}
		load();
		text.setTextAlign(Paint.Align.CENTER);
		text.setFakeBoldText(true);
		stroke.setStyle(Paint.Style.STROKE);
	}

	// ---- configuração ----

	public void setKeySink(KeySink s) { sink = s; }
	public void setListener(Listener l) { listener = l; }
	public void setBuyZone(boolean b) { if (buyZone != b) { buyZone = b; postInvalidate(); } }
	public void setControlsOn(boolean b) { controlsOn = b; if (!b) releaseAll(); postInvalidate(); }
	public void setEditMode(boolean b) {
		editMode = b;
		if (b) releaseAll();
		claimed.clear();
		sdlPtrs.clear();
		selected = null;
		postInvalidate();
	}
	public boolean isEditMode() { return editMode; }

	private void load() {
		opacity = sp.getFloat("opacity", 0.55f);
		for (Ctl c : ctls) {
			String v = sp.getString("c_" + c.id, null);
			if (v == null) continue;
			try {
				String[] p = v.split(",");
				c.x = Float.parseFloat(p[0]);
				c.y = Float.parseFloat(p[1]);
				c.size = Float.parseFloat(p[2]);
			} catch (RuntimeException ignored) { }
		}
	}

	public void save() {
		SharedPreferences.Editor e = sp.edit();
		e.putFloat("opacity", opacity);
		for (Ctl c : ctls) e.putString("c_" + c.id, c.x + "," + c.y + "," + c.size);
		e.apply();
	}

	private void resetDefaults() {
		for (Ctl c : ctls) { c.x = c.dx; c.y = c.dy; c.size = c.dsize; }
		opacity = 0.55f;
	}

	// ---- teclas ----

	private void key(int code, boolean down) {
		if (sink == null) return;
		if (down) sink.down(code); else sink.up(code);
	}

	public void releaseAll() {
		for (Ctl c : ctls) {
			if (c.pointer != -1 && !c.isStick()) key(c.key, false);
			c.pointer = -1;
		}
		stickUpdate(0, 0);
		gearPointer = -1;
	}

	private void stickUpdate(float vx, float vy) {
		knobX = vx;
		knobY = vy;
		boolean f = vy < -0.35f, b = vy > 0.35f, l = vx < -0.35f, r = vx > 0.35f;
		if (f != kF) { kF = f; key(KeyEvent.KEYCODE_W, f); }
		if (b != kB) { kB = b; key(KeyEvent.KEYCODE_S, b); }
		if (l != kL) { kL = l; key(KeyEvent.KEYCODE_A, l); }
		if (r != kR) { kR = r; key(KeyEvent.KEYCODE_D, r); }
	}

	// ---- geometria ----

	private float cx(Ctl c) { return c.x * getWidth(); }
	private float cy(Ctl c) { return c.y * getHeight(); }
	private float rad(Ctl c) { return c.size * getHeight() / 2f; }

	private boolean visible(Ctl c) {
		if (editMode) return true;
		if (c.id.equals("buy")) return controlsOn && buyZone;
		return controlsOn;
	}

	private Ctl hit(float x, float y) {
		for (int i = ctls.size() - 1; i >= 0; i--) {
			Ctl c = ctls.get(i);
			if (!visible(c)) continue;
			float r = rad(c) * (c.isStick() ? 1.3f : 1.0f);
			float dx = x - cx(c), dy = y - cy(c);
			if (dx * dx + dy * dy <= r * r) return c;
		}
		return null;
	}

	// ---- desenho ----

	@Override
	protected void onDraw(Canvas cv) {
		int w = getWidth(), h = getHeight();
		float gr = h * 0.045f;
		gearRect.set(w * 0.30f - gr, h * 0.07f - gr, w * 0.30f + gr, h * 0.07f + gr);
		eyeRect.set(w * 0.345f - gr, h * 0.07f - gr, w * 0.345f + gr, h * 0.07f + gr);

		if (editMode) {
			fill.setColor(0xB0000000);
			cv.drawRect(0, 0, w, h, fill);
		}

		for (Ctl c : ctls) {
			if (!visible(c)) continue;
			float x = cx(c), y = cy(c), r = rad(c);
			int a = (int) (255 * opacity);
			boolean pressed = c.pointer != -1;
			if (c.isStick()) {
				fill.setColor(0x00FFFFFF | (a / 3 << 24));
				cv.drawCircle(x, y, r, fill);
				stroke.setStrokeWidth(h * 0.006f);
				stroke.setColor(0x00FFFFFF | (Math.min(255, a + 60) << 24));
				cv.drawCircle(x, y, r, stroke);
				fill.setColor(0x00FFFFFF | (Math.min(255, a + 80) << 24));
				cv.drawCircle(x + knobX * r * 0.55f, y + knobY * r * 0.55f, r * 0.38f, fill);
			} else {
				boolean buy = c.id.equals("buy");
				int rgb = buy ? 0xF2C200 : (c.id.equals("fire") ? 0xE23B3B : 0xFFFFFF);
				fill.setColor((rgb & 0xFFFFFF) | ((pressed ? Math.min(255, a + 90) : a / 2 + 20) << 24));
				cv.drawCircle(x, y, r, fill);
				stroke.setStrokeWidth(h * 0.004f);
				stroke.setColor((rgb & 0xFFFFFF) | (Math.min(255, a + 80) << 24));
				cv.drawCircle(x, y, r, stroke);
				text.setColor(0xFFFFFFFF);
				text.setTextSize(Math.min(r * 0.62f, r * 2f * 0.9f / Math.max(2, c.label.length()) * 1.5f));
				cv.drawText(c.label, x, y + text.getTextSize() * 0.35f, text);
			}
			if (editMode && c == selected) {
				stroke.setStrokeWidth(h * 0.008f);
				stroke.setColor(0xFF4FC3F7);
				cv.drawCircle(x, y, r + h * 0.012f, stroke);
			}
		}

		// engrenagem (editar) e olho (mostrar/ocultar controles)
		drawRound(cv, gearRect, editMode ? "OK" : "EDIT", editMode ? 0xCC4FC3F7 : 0x66000000);
		if (!editMode) drawRound(cv, eyeRect, controlsOn ? "ON" : "OFF", controlsOn ? 0x66000000 : 0xCCB71C1C);

		if (editMode) drawPanel(cv, w, h);
	}

	private void drawRound(Canvas cv, RectF r, String label, int color) {
		fill.setColor(color);
		cv.drawRoundRect(r, r.height() / 3, r.height() / 3, fill);
		text.setColor(0xFFFFFFFF);
		text.setTextSize(r.height() * 0.38f);
		cv.drawText(label, r.centerX(), r.centerY() + text.getTextSize() * 0.35f, text);
	}

	private void drawPanel(Canvas cv, int w, int h) {
		text.setColor(0xFFFFFFFF);
		text.setTextSize(h * 0.045f);
		String hint = selected == null
				? "Toque num botão e arraste para mover"
				: "Arraste para mover  |  - / + muda o tamanho";
		cv.drawText(hint, w / 2f, h * 0.28f, text);

		panelRects.clear();
		panelActions.clear();
		panelLabels.clear();
		String[] labels = { "TAMANHO -", "TAMANHO +", "TRANSP. -", "TRANSP. +", "PADRÃO", "SALVAR" };
		int[] acts = { A_SMALLER, A_BIGGER, A_LESS, A_MORE, A_RESET, A_SAVE };
		float bw = w * 0.14f, bh = h * 0.12f, gap = w * 0.01f;
		float total = labels.length * bw + (labels.length - 1) * gap;
		float x0 = (w - total) / 2f, y0 = h * 0.86f - bh / 2f;
		for (int i = 0; i < labels.length; i++) {
			RectF r = new RectF(x0 + i * (bw + gap), y0, x0 + i * (bw + gap) + bw, y0 + bh);
			panelRects.add(r);
			panelActions.add(acts[i]);
			panelLabels.add(labels[i]);
			int col = acts[i] == A_SAVE ? 0xEE2E7D32 : (acts[i] == A_RESET ? 0xEEB71C1C : 0xEE37474F);
			fill.setColor(col);
			cv.drawRoundRect(r, bh / 4, bh / 4, fill);
			text.setTextSize(bh * 0.30f);
			cv.drawText(labels[i], r.centerX(), r.centerY() + text.getTextSize() * 0.35f, text);
		}
	}

	// ---- toque ----

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return editMode && editTouch(e);
	}

	private boolean editTouch(MotionEvent e) {
		float x = e.getX(), y = e.getY();
		switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				if (gearRect.contains(x, y)) { finishEdit(true); return true; }
				for (int i = 0; i < panelRects.size(); i++) {
					if (panelRects.get(i).contains(x, y)) { panelAction(panelActions.get(i)); return true; }
				}
				Ctl c = hit(x, y);
				selected = c;
				dragging = c;
				if (c != null) { dragOffX = cx(c) - x; dragOffY = cy(c) - y; }
				invalidate();
				return true;
			}
			case MotionEvent.ACTION_MOVE:
				if (dragging != null && getWidth() > 0) {
					dragging.x = clamp((x + dragOffX) / getWidth(), 0.02f, 0.98f);
					dragging.y = clamp((y + dragOffY) / getHeight(), 0.04f, 0.96f);
					invalidate();
				}
				return true;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				dragging = null;
				return true;
			default:
				return true;
		}
	}

	private void panelAction(int a) {
		switch (a) {
			case A_SMALLER: if (selected != null) selected.size = clamp(selected.size * 0.9f, 0.07f, 0.8f); break;
			case A_BIGGER: if (selected != null) selected.size = clamp(selected.size * 1.1f, 0.07f, 0.8f); break;
			case A_LESS: opacity = clamp(opacity - 0.08f, 0.15f, 1f); break;
			case A_MORE: opacity = clamp(opacity + 0.08f, 0.15f, 1f); break;
			case A_RESET: resetDefaults(); break;
			case A_SAVE: finishEdit(true); return;
		}
		invalidate();
	}

	private void finishEdit(boolean save) {
		if (save) save();
		if (listener != null) listener.onEditDone(); else setEditMode(false);
	}

	// Pointers (dedos) que estão em controles nossos; os demais vão para o jogo (câmera/menus).
	private final java.util.HashSet<Integer> claimed = new java.util.HashSet<>();
	private final java.util.ArrayList<Integer> sdlPtrs = new java.util.ArrayList<>();

	private boolean claimDown(int id, float x, float y) {
		if (gearRect.contains(x, y)) { setEditMode(true); return true; }
		if (eyeRect.contains(x, y)) { setControlsOn(!controlsOn); return true; }
		Ctl c = hit(x, y);
		if (c == null) return false;
		c.pointer = id;
		if (c.isStick()) stickMove(c, x, y); else key(c.key, true);
		invalidate();
		return true;
	}

	private void releasePointer(int id) {
		for (Ctl c : ctls) {
			if (c.pointer != id) continue;
			if (c.isStick()) stickUpdate(0, 0); else key(c.key, false);
			c.pointer = -1;
		}
		invalidate();
	}

	/**
	 * Recebe TODOS os toques da tela do jogo. Os dedos que caem em nossos botões são tratados aqui;
	 * os outros são repassados ao SDL como um evento só com esses dedos (câmera e menus continuam
	 * funcionando ao mesmo tempo que o joystick).
	 */
	public boolean surfaceTouch(View v, MotionEvent e, View.OnTouchListener sdl) {
		if (editMode) return true;
		int act = e.getActionMasked(), ai = e.getActionIndex();
		int actId = e.getPointerId(ai);
		boolean actClaimed = false;

		if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
			if (claimDown(actId, e.getX(ai), e.getY(ai))) { claimed.add(actId); actClaimed = true; }
		} else if (act == MotionEvent.ACTION_MOVE) {
			boolean any = false;
			for (Ctl c : ctls) {
				if (c.pointer == -1 || !c.isStick()) continue;
				int i = e.findPointerIndex(c.pointer);
				if (i >= 0) { stickMove(c, e.getX(i), e.getY(i)); any = true; }
			}
			if (any) invalidate();
		} else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
			actClaimed = claimed.contains(actId);
		}

		// dedos que vão para o jogo
		java.util.ArrayList<Integer> idxs = new java.util.ArrayList<>();
		for (int i = 0; i < e.getPointerCount(); i++) {
			int id = e.getPointerId(i);
			if (!claimed.contains(id)) idxs.add(i);
		}
		if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP || act == MotionEvent.ACTION_CANCEL) {
			// libera depois de montar a lista
		}

		boolean result;
		if (act == MotionEvent.ACTION_CANCEL) {
			releaseAll();
			claimed.clear();
			sdlPtrs.clear();
			result = sdl.onTouch(v, e);
			return result;
		}

		int newAction = MotionEvent.ACTION_MOVE;
		int newIndex = -1;
		boolean add = false, remove = false;
		if (!actClaimed && (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN)) {
			newIndex = idxs.indexOf(ai);
			add = true;
			newAction = sdlPtrs.isEmpty() ? MotionEvent.ACTION_DOWN
					: (MotionEvent.ACTION_POINTER_DOWN | (newIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
		} else if (!actClaimed && (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP)) {
			newIndex = idxs.indexOf(ai);
			remove = true;
			newAction = sdlPtrs.size() <= 1 ? MotionEvent.ACTION_UP
					: (MotionEvent.ACTION_POINTER_UP | (newIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
		}

		if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
			if (actClaimed) { claimed.remove(actId); releasePointer(actId); }
		}

		// nada para o jogo neste evento (o dedo que mudou é nosso e não há outros), ou evento de dedo nosso
		if (idxs.isEmpty() || (newIndex < 0 && (add || remove))) {
			if (remove) sdlPtrs.remove(Integer.valueOf(actId));
			return true;
		}
		if (actClaimed && sdlPtrs.isEmpty()) return true;

		MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[idxs.size()];
		MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[idxs.size()];
		for (int k = 0; k < idxs.size(); k++) {
			props[k] = new MotionEvent.PointerProperties();
			coords[k] = new MotionEvent.PointerCoords();
			e.getPointerProperties(idxs.get(k), props[k]);
			e.getPointerCoords(idxs.get(k), coords[k]);
		}
		MotionEvent ne = MotionEvent.obtain(e.getDownTime(), e.getEventTime(), newAction, idxs.size(), props, coords,
				e.getMetaState(), e.getButtonState(), e.getXPrecision(), e.getYPrecision(), e.getDeviceId(),
				e.getEdgeFlags(), e.getSource(), e.getFlags());
		result = sdl.onTouch(v, ne);
		ne.recycle();
		if (add) sdlPtrs.add(actId);
		if (remove) sdlPtrs.remove(Integer.valueOf(actId));
		return result;
	}

	private void stickMove(Ctl c, float x, float y) {
		float r = rad(c);
		float vx = (x - cx(c)) / r, vy = (y - cy(c)) / r;
		float m = (float) Math.hypot(vx, vy);
		if (m > 1f) { vx /= m; vy /= m; }
		stickUpdate(vx, vy);
	}

	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
