#!/usr/bin/env python3
"""Aplica as modificações do Counter-A-Launcher sobre o Xash3D FWGS e o cs16-client."""
import os
import pathlib
import shutil
import sys

engine = pathlib.Path(sys.argv[1])
cs16 = pathlib.Path(sys.argv[2])
root = pathlib.Path(__file__).resolve().parent.parent
app = root / "app"


def sub(path, old, new, all_=False):
    text = path.read_text()
    if old not in text:
        sys.exit(f"ERRO: trecho não encontrado em {path}:\n{old}")
    path.write_text(text.replace(old, new) if all_ else text.replace(old, new, 1))


# ---- motor (app Android) ----
gradle = engine / "android/app/build.gradle.kts"
sub(gradle, 'applicationId = "su.xash.engine"', 'applicationId = "app.counter.launcher"')
sub(gradle, 'versionName = "0.21-" + getGitHash()', 'versionName = "build-%s"' % os.environ.get("BUILD_NUMBER", "0"))
sub(gradle, 'applicationIdSuffix = ".test"', 'applicationIdSuffix = ""', all_=True)
sub(gradle, 'setOf("armeabi-v7a", "arm64-v8a", "x86")', 'setOf("armeabi-v7a", "arm64-v8a")')
sub(gradle, "useLegacyPackaging = true",
    'useLegacyPackaging = true\n\t\t\tpickFirsts.add("**/libc++_shared.so")')

with open(engine / "android/app/proguard-rules.pro", "a") as f:
    f.write("\n-keep class app.counter.launcher.** { *; }\n")

shutil.copyfile(app / "AndroidManifest.xml", engine / "android/app/src/main/AndroidManifest.xml")
dst = engine / "android/app/src/main/java/app/counter/launcher"
dst.mkdir(parents=True, exist_ok=True)
for f in (app / "java/app/counter/launcher").glob("*.java"):
    shutil.copyfile(f, dst / f.name)

xml = engine / "android/app/src/main/res/xml"
xml.mkdir(parents=True, exist_ok=True)
shutil.copyfile(app / "res/xml/counter_file_paths.xml", xml / "counter_file_paths.xml")

touch = engine / "engine/client/input/in_touch.c"
sub(touch, '#include "mobility_int.h"', '#include "mobility_int.h"\n#include <stdlib.h>')
sub(touch, "static qboolean Touch_IsVisible( touch_button_t *button )\n{",
    "static qboolean Touch_IsVisible( touch_button_t *button )\n{\n"
    "\t// Counter-A-Launcher: botões de toque do motor desligados (o app desenha os dele)\n"
    "\tstatic int noButtons = -1;\n"
    "\tif( noButtons < 0 ) noButtons = getenv( \"COUNTER_NO_TOUCH_BUTTONS\" ) != NULL;\n"
    "\tif( noButtons && button->type != touch_look ) return false;\n")
# a área de olhar (botão invisível _look) passa a ocupar a tela toda; o app tira do caminho os dedos que caem nos botões dele
sub(touch, "Q_strncpy( b->name, name, sizeof( b->name ));\n\tb->x1 = x1;",
    "Q_strncpy( b->name, name, sizeof( b->name ));\n"
    "\tif( !Q_strcmp( command, \"_look\" )) { x1 = 0.0f; y1 = 0.0f; x2 = 1.0f; y2 = 1.0f; } // Counter-A-Launcher\n"
    "\tb->x1 = x1;")

# ---- cs16-client (bibliotecas do jogo) ----
cg = cs16 / "android/app/build.gradle"
sub(cg, 'abiFilters "armeabi-v7a", "arm64-v8a", "x86"', 'abiFilters "armeabi-v7a", "arm64-v8a"')
sub(cg, "signingConfig = signingConfigs.continuous", "signingConfig = signingConfigs.debug")

icons = cs16 / "cl_dll/status_icons.cpp"
sub(icons, "#include <stdio.h>", "#include <stdio.h>\n#include <stdlib.h>")
sub(icons, "char *pszIconName = reader.ReadString();",
    """char *pszIconName = reader.ReadString();

	// Counter-A-Launcher: avisa o app Android quando o jogador entra/sai da zona de compra
	if ( !stricmp( pszIconName, "buyzone" ) )
	{
		const char *stateDir = getenv( "COUNTER_STATE_DIR" );
		if ( stateDir )
		{
			char statePath[512];
			snprintf( statePath, sizeof statePath, "%s/counter_state", stateDir );
			FILE *stateFile = fopen( statePath, "w" );
			if ( stateFile )
			{
				fputc( ShouldEnable ? '1' : '0', stateFile );
				fclose( stateFile );
			}
		}
	}""")
print("Modificações aplicadas.")
