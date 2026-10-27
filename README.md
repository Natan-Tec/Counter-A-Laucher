# Counter-A-Launcher

CS 1.6 simples para Android (feito para o Galaxy A11): abre, escolhe a pasta do jogo e joga.
Base: motor [Xash3D FWGS](https://github.com/FWGS/xash3d-fwgs) + bibliotecas do CS de [Velaron/cs16-client](https://github.com/Velaron/cs16-client).
Os arquivos do jogo (pastas `valve` e `cstrike`) **não** vêm no app: você aponta a pasta onde eles estão.

## O que tem
- Tela inicial: escolher pasta, modo (partida rápida com bots ou menu do jogo), mapa, bots, qualidade, FPS, sensibilidade.
- Joystick sempre visível; arrastar a tela livre para mirar; menus do jogo (CT/TR, compra) tocáveis.
- Botões: fogo, pular, agachar, recarregar, usar, TAB, MENU e armas 1–5 (as teclas 1–5 também escolhem opções dos menus do CS).
- Botão **COMPRAR** só aparece dentro da zona de compra.
- **Editar botões**: mover, mudar tamanho e transparência (pela tela inicial ou pelo botão EDIT dentro do jogo).

## Gerar o APK
1. Suba estes arquivos neste repositório (branch `main`).
2. Aba **Actions** → **Gerar APK** → **Run workflow** (demora bastante na primeira vez).
3. Ao terminar, baixe o artefato **Counter-A-Launcher** (o `.apk`) e instale no celular.

## Entrando na partida e escolhendo CT/TR
- **Partida rápida com bots**: o app abre direto no mapa. A tela de times aparece sozinha; toque na opção ou use os botões 1 (TR), 2 (CT) ou 5 (automático).
- **Menu do jogo**: abre o menu do Xash/CS normal (servidores online etc.); os botões de combate começam ocultos (botão ON/OFF no topo liga/desliga).

## Observações
- Fixa versões do Xash3D FWGS e cs16-client em `.github/workflows/build.yml` para o build não quebrar.
- Nenhuma parte foi compilada nem testada em aparelho ainda; o primeiro build pode precisar de ajustes.
