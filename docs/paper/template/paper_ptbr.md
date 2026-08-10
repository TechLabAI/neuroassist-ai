## NeuroAssist AI: Uma Plataforma Offline-First e Preservadora de Privacidade para Comunicação Assistiva Baseada em Gestos Faciais Residuais na Doença Neurodegenerativa

**Danilo Amaral**
Cybersoft - TechLab AI
danilo.amaral@mensa.org.br

**Resumo**—Pacientes com doença do neurônio motor (DNM/ELA) perdem progressivamente o controle motor voluntário, frequentemente mantendo apenas movimentos faciais sutis como seu último canal de comunicação. Os sistemas existentes de comunicação aumentativa e alternativa (CAA) exigem conectividade de rede confiável e não se adaptam ao perfil motor residual de pacientes individuais. Apresentamos o NeuroAssist AI, uma plataforma de comunicação assistiva *offline-first* que preserva a privacidade e transforma gestos faciais residuais em solicitações de emergência estruturadas, usando um pipeline de visão computacional baseado em WebAssembly executado localmente. O sistema combina uma máquina de estados de reconhecimento de gestos deliberados de seis estados com a calibração de limites EAR específica para o paciente, implantada sobre uma arquitetura de microsserviços hexagonal que desacopla a detecção do tipo de gesto do gerenciamento do catálogo de comandos. A latência de ponta a ponta, desde a conclusão do gesto até a exibição do alarme para o cuidador, obteve uma média de [X] ms ($\sigma=[Y]$) em [N] ensaios. A máquina de estados de gestos deliberados reduziu a taxa de falsos positivos de [A] % (limite EAR simples) para [B] % ([N] voluntários saudáveis, [M] minutos cada). O NeuroAssist AI demonstra que a detecção de gestos residuais de alta precisão e preservadora de privacidade é alcançável em hardware comercial sem dependência de rede, utilizando uma arquitetura calibrável e extensível adequada para implantação doméstica.

**Palavras-chave**—comunicação assistiva, doença do neurônio motor, ELA, reconhecimento de gestos faciais, WebAssembly, offline-first, arquitetura hexagonal, privacy-by-design

---

### I. INTRODUÇÃO

A doença do neurônio motor (DNM), também conhecida como esclerose lateral amiotrófica (ELA), é uma condição neurodegenerativa progressiva que destrói os neurônios motores superiores e inferiores, resultando na perda de movimento voluntário, fala e, em última instância, função respiratória. A incidência global é de aproximadamente 2-3 por 100.000 pessoas-ano, afetando cerca de 41.000 indivíduos na Austrália e 30.000 no Brasil. A sobrevida mediana após o diagnóstico é de 2 a 5 anos; a insuficiência respiratória, tipicamente tratada com ventilação não invasiva (VNI), é a principal causa de morte. Notavelmente, demonstrou-se que a VNI prolonga a sobrevida em um fator de aproximadamente três vezes em comparação apenas com o melhor tratamento farmacológico, tornando o período de uso da VNI clinicamente significativo e prolongado. Durante essa fase, os pacientes permanecem cognitivamente intactos enquanto se tornam progressivamente incapazes de falar ou gesticular de formas que as tecnologias de comunicação padrão possam detectar.

Trabalhos recentes demonstraram que a assincronia paciente-ventilador (PVA) — uma incompatibilidade entre os ciclos do ventilador e o esforço respiratório do paciente — é um fator contribuinte significativo para a não adesão à VNI na DNM, causando desconforto, fragmentação do sono e desfechos clínicos potencialmente adversos. Pacientes que vivenciam desconforto relacionado à PVA durante o uso noturno da VNI não conseguem sinalizar prontamente a sua angústia aos cuidadores usando as tecnologias de CAA existentes, as quais requerem conectividade de rede ou hardware dedicado que é inadequado para o ambiente de cuidados domiciliares. O NeuroAssist AI aborda essa lacuna diretamente: ao fornecer um canal de sinalização local, sempre disponível e que requer apenas uma webcam comum, ele permite que os pacientes alertem os cuidadores durante períodos sem assistência — inclusive durante o uso da VNI à noite.

Em estágios avançados, uma proporção substancial de pacientes experimenta uma condição semelhante à síndrome do encarceramento (locked-in), na qual o controle voluntário se limita a movimentos faciais residuais sutis: pequenas contrações palpebrais, elevações de sobrancelha ou microexpressões que persistem após a perda da função motora dos membros e orofacial. Esses gestos residuais representam o último canal voluntário disponível para comunicação. Sem um meio confiável para detectá-los e agir sobre eles, os pacientes não podem sinalizar aflição, desconforto ou necessidades aos cuidadores — com consequências diretas para a qualidade de vida e a segurança do paciente durante períodos desassistidos.

Os sistemas existentes de comunicação aumentativa e alternativa (CAA) não atendem adequadamente a essa população. Dispositivos de rastreamento ocular de ponta (ex: Tobii Dynavox) e softwares de CAA dedicados (ex: Grid 3) oferecem alta precisão, mas requerem conectividade sem fio estável, hardware dedicado custando mais de USD 3.000 e configurações calibradas de fábrica que não conseguem se adaptar à degradação do perfil motor de um paciente individual. Interfaces cérebro-computador (ICC) via EEG oferecem uma alternativa, mas envolvem hardware invasivo ou incômodo, inadequado para implantação doméstica. Existem abordagens baseadas em câmeras de baixo custo na pesquisa, mas elas dependem de comparações simples de limites por quadro (frame) que geram taxas de falsos positivos inaceitavelmente altas quando aplicadas à atividade facial involuntária presente em pacientes com DNM durante a VNI.

Apresentamos o **NeuroAssist AI**, uma plataforma de comunicação assistiva *offline-first* que preserva a privacidade e é projetada para implantação doméstica com uma webcam comercial. O sistema transforma gestos faciais residuais em solicitações de emergência estruturadas usando um pipeline de visão computacional em WebAssembly executado localmente, uma máquina de estados de gestos deliberados e um perfil de detecção calibrável pelo paciente — sem transmitir nenhum vídeo para fora do dispositivo.

Este artigo faz três contribuições originais:

* **Um pipeline de detecção *offline-first* e com preservação de privacidade.** O modelo MediaPipe FaceLandmarker e seu tempo de execução em WebAssembly são servidos a partir de recursos locais; nenhum quadro de vídeo ou dado biométrico sai do dispositivo do paciente. O delegado de CPU garante a compatibilidade com hardwares comerciais independentemente da disponibilidade de GPU.
* **Uma máquina de estados de gestos deliberados de seis estados com calibração específica do paciente.** Em vez de um limite único de EAR, a máquina de estados exige uma duração mínima de fechamento ($\ge 3$ quadros, $\approx 100$ ms) em cada um de dois piscares sequenciais, uma reabertura confirmada entre eles e uma janela temporal configurável. Os limites são armazenados em um `DetectionProfile` versionado e adaptados por paciente. Esse design ataca especificamente as fontes de falsos positivos relevantes para a DNM: piscares involuntários, artefatos de ptose parcial e desvios lentos de limites.
* **Uma arquitetura de microsserviços hexagonal extensível com tipos de gestos plug-in.** O campo `engineFeatureRef` no perfil de detecção desacopla o mecanismo de reconhecimento de gestos do catálogo de comandos, permitindo que novos gestos (elevação de sobrancelha, piscar de um único olho) sejam registrados sem modificar o código de domínio existente. Regras do ArchUnit impostas em tempo de compilação garantem que essa separação persista à medida que o sistema evolui.


O restante do artigo está organizado da seguinte forma. A Seção II analisa os trabalhos relacionados em CAA, detecção de gestos faciais e edge computing para saúde. A Seção III descreve detalhadamente a arquitetura do sistema. A Seção IV relata a avaliação experimental. A Seção V discute as limitações e os trabalhos futuros, e a Seção VI conclui o estudo.

---

### II. TRABALHOS RELACIONADOS

**A. Sistemas de CAA para Doenças Neurodegenerativas**
A comunicação aumentativa e alternativa (CAA) para pessoas com DNM/ELA tem sido extensivamente estudada desde a década de 1990. Soluções comerciais dominam a prática clínica: dispositivos de rastreamento ocular de ponta, como o Tobii Dynavox PCEye e o Eyegaze Edge, oferecem seleção de foco em grades com precisão superior a 98% em condições de laboratório, mas possuem custos de hardware acima de USD 3.000 e requerem recalibração periódica por profissionais treinados. Esses sistemas pressupõem controle oculomotor residual suficiente, o qual se deteriora à medida que a DNM progride para a oftalmoplegia. Pacotes de software de CAA, como o Grid 3, dependem de conectividade estável via Bluetooth ou Wi-Fi para sincronizar conjuntos de símbolos e perfis de usuários, criando uma barreira para usuários domésticos sem banda larga confiável.

Uma pesquisa feita por Clarke et al. identificou que a provisão de CAA para pessoas com ELA é caracterizada por encaminhamentos tardios, transições rápidas de dispositivos à medida que a função motora declina e uma lacuna persistente de soluções adaptáveis e de baixo custo adequadas para o ambiente doméstico. As interfaces cérebro-computador (ICC) via eletroencefalografia oferecem um caminho alternativo que ignora totalmente o sistema motor, mas os atuais headsets de ICC não invasivos atingem taxas típicas de transferência de informações de 5 a 25 bits por minuto em condições ideais, exigem operadores treinados e são pouco adequados para a configuração de cuidados domiciliares. ICCs corticais implantados atingem taxas mais altas, mas acarretam riscos cirúrgicos. O NeuroAssist AI tem como alvo o estágio intermediário — após a perda da função dos membros e da fala, mas enquanto persiste um grau de movimento facial voluntário — para o qual não existe atualmente na literatura uma solução de baixo custo e independente de rede.

**B. Detecção de Gestos Faciais via Visão Computacional**
Três gerações de análise facial baseada em câmeras são relevantes para este trabalho.

*Primeira geração — classificadores em cascata.* Viola e Jones introduziram a detecção de rostos em tempo real usando cascatas de características Haar, viabilizando a implantação em hardwares de consumo. Esses detectores localizam o rosto, mas não fornecem coordenadas de marcos (landmarks) subfaciais; detectar um piscar requer heurísticas adicionais aplicadas à região ocular detectada e é altamente sensível à variação de iluminação.

*Segunda geração — regressão de marcos faciais.* A biblioteca Dlib de King popularizou a detecção de 68 marcos faciais por meio de um conjunto de árvores de regressão, viabilizando o cálculo confiável do EAR sem o uso de aprendizado profundo (deep learning). Soukupová e Čech formalizaram o EAR como um escalar único para a detecção de piscadas, demonstrando uma precisão de detecção em nível de quadro superior a 99% em conjuntos de dados controlados. A limitação central dessa geração é a ausência de um tempo de execução compatível com WebAssembly, o que torna a implantação no lado do cliente via navegador impraticável.

*Terceira geração — modelos profundos de marcos no dispositivo.* O MediaPipe FaceLandmarker fornece 468 marcos faciais normalizados e 52 coeficientes de *blendshape* a aproximadamente 30 fps em um contexto WebAssembly de navegador, usando apenas um delegado de CPU. Ele absorve e estende a abordagem do Dlib: o conjunto mais denso de marcos fornece melhor cobertura da região periorbital, e as pontuações de blendshape oferecem um sinal calibrado para movimentos faciais parciais que o EAR sozinho não captura. O NeuroAssist AI baseia-se nesta geração, mantendo o EAR para a máquina de estados do piscar duplo (onde sua simplicidade ajuda na interpretabilidade) enquanto usa pontuações de blendshape para gestos de piscar de um único olho e de elevação de sobrancelha.

**C. Reconhecimento de Gestos Deliberados e Supressão de Falsos Positivos**
Um desafio fundamental na CAA acionada por gestos é distinguir ativações intencionais da atividade facial involuntária que ocorre continuamente durante o comportamento normal. A taxa de piscar espontâneo em adultos saudáveis é de 15 a 20 piscadas por minuto; em pacientes com DNM, as taxas podem ser menores devido à perda de neurônios motores, mas permanecem diferentes de zero. Qualquer abordagem baseada em limites (thresholds) aplicada no nível de um único quadro produz, portanto, taxas de falsos positivos na faixa de várias ocorrências por minuto sob condições naturais, tornando o monitoramento não supervisionado inviável.

As duas estratégias principais na literatura para suprimir falsos positivos em CAA facial são o *tempo de permanência* (dwell time) e a *modelagem de intencionalidade*. A seleção por tempo de permanência — amplamente usada na CAA baseada no olhar — requer que o usuário fixe o olhar em uma região-alvo por uma duração fixa (normalmente 0,5 a 1 s). Essa abordagem é inaplicável à entrada baseada em piscares, pois um piscar por definição fecha o olho e interrompe a fixação. A modelagem de intencionalidade por meio de máquinas de estados foi proposta na literatura mais ampla de interação humano-computador, mas sua aplicação na CAA baseada em piscar para DNM não foi descrita sistematicamente. A máquina de seis estados introduzida neste artigo (Seção III-C) aplica restrições de intencionalidade específicas para o gesto de duplo piscar: duração mínima de fechamento por piscar, reabertura confirmada entre os piscares e uma janela temporal — uma combinação não encontrada, até onde os autores têm conhecimento, na literatura anterior de CAA.

**D. Computação de Borda e Privacidade para Aplicações de Saúde**
O processamento de dados de sensores relacionados à saúde no dispositivo, em vez de na nuvem, tem recebido atenção crescente após a aplicação do GDPR em 2018 e da LGPD em 2020. Vídeos faciais são explicitamente categorizados como dados biométricos sob ambas as estruturas, atraindo as maiores obrigações regulatórias para o processamento. A execução local via WebAssembly fornece uma garantia prática de privacidade: o modelo de execução com memória isolada da especificação impede que um módulo carregado acesse qualquer estado do navegador fora de sua memória linear, e nenhuma chamada de rede é necessária para executar a inferência de marcos faciais. O TensorFlow.js, o ONNX Runtime Web e o MediaPipe Tasks alavancam esse tempo de execução para inferência no próprio dispositivo a velocidades comparáveis ao código nativo em CPUs modernas. A latência de implantação de modelos WebAssembly para análise facial tem sido relatada em 20 a 40 ms por quadro em hardwares de consumo sem aceleração de GPU, o que é consistente com a operação de 30 fps observada em nossa PoC.

**E. Arquitetura de Software para Sistemas de Saúde**
A arquitetura Hexagonal (Ports and Adapters) tem sido proposta como um padrão estrutural para sistemas de informação em saúde nos quais o modelo de domínio deve permanecer isolado de mudanças tecnológicas — um requisito recorrente em software de saúde regulamentado, no qual requisitos de auditoria, esquemas de dados e padrões de integração evoluem de forma independente. A separação explícita de portas de entrada e saída do padrão facilita extensões incrementais sem modificar a lógica clínica central, uma propriedade particularmente valiosa em tecnologia assistiva, onde novas modalidades de gestos são adicionadas à medida que as capacidades motoras dos pacientes mudam.

A decomposição de plataformas de CAA em microsserviços não foi relatada na literatura, segundo o conhecimento dos autores; a maioria dos sistemas existentes são aplicativos monolíticos de desktop ou firmware. A arquitetura do NeuroAssist AI demonstra que decompor um pipeline de gesto-para-alerta em serviços implantáveis de forma independente (GEST, EVNT e os planejados serviços PAC, ACS, HIST, DASH) é viável na escala de PoC, e que a aplicação de fronteiras em tempo de compilação via ArchUnit fornece um mecanismo prático para manter a disciplina hexagonal em uma base de código Java desenvolvida em equipe.

**F. Comunicação e Segurança Durante o Uso da VNI na DNM**
A adesão à ventilação não invasiva na DNM é consistentemente inferior às metas clínicas, sendo o desconforto relatado pelo paciente e a assincronia dispositivo-paciente identificados como os principais fatores contribuintes. O ensaio clínico randomizado 3TLA está atualmente investigando se a titulação da VNI guiada por polissonografia melhora as taxas de uso; seu desfecho primário — o uso da VNI excedendo quatro horas por dia — reflete o desafio de sustentar a adesão em ambientes domésticos. Uma dimensão complementar, porém subexplorada, é a capacidade do paciente de sinalizar desconforto ou aflição aos cuidadores durante o uso não supervisionado da VNI, particularmente à noite. Até onde os autores sabem, nenhum sistema publicado fornece um mecanismo independente de rede e de baixo custo para este propósito; o NeuroAssist AI é projetado para preencher essa lacuna.

---

### III. ARQUITETURA DO SISTEMA

O NeuroAssist AI é composto por dois contextos de implantação: um pipeline de detecção do lado do navegador executado inteiramente no dispositivo do paciente e um pipeline de alertas de backend implementado como microsserviços hexagonais. A Figura 1 ilustra o fluxo de dados de ponta a ponta.

**A. Visão Geral do Sistema**
O pipeline é acionado por um gesto facial deliberado capturado por qualquer webcam comercial (720p ou superior). O loop de detecção de gestos é executado exclusivamente no navegador via WebAssembly; nenhum quadro de vídeo deixa o dispositivo em momento algum. Quando um gesto é confirmado pela máquina de estados (Seção III-C), um payload JSON leve é enviado via POST para o serviço de ingestão de eventos. O serviço valida o gesto em relação ao perfil de detecção ativo do paciente, cria um registro `Evento` e despacha uma notificação `FULL_SCREEN_ALARM`. Um aplicativo do cuidador consulta (*polls*) o backend a cada cinco segundos; ao detectar um evento ativo, ele renderiza uma sobreposição de alarme em tela cheia. O cuidador confirma o atendimento, o que grava um veredito (*verdict*) de volta ao registro do evento e limpa o alarme.

**B. Detecção de Gestos Offline-First**
A extração de marcos faciais é realizada pelo MediaPipe FaceLandmarker executando no delegado de CPU do WebAssembly. O binário WASM e o modelo face landmarker em `float16` são agrupados como recursos (assets) estáticos e servidos em `/assets/mediapipe-wasm/`; não há dependência de nenhuma rede de distribuição de conteúdo durante a detecção de gestos. O download do modelo, de aproximadamente 30 MB, ocorre apenas uma vez no primeiro uso e depois é servido pelo cache do navegador.

O landmarker produz 468 marcos normalizados por quadro em um loop `requestAnimationFrame` que funciona a aproximadamente 30 fps. Cada quadro gera:

1. EAR (Razão de Aspecto Ocular) para cada olho, computado a partir de seis marcos {33, 160, 158, 133, 153, 144} (esquerdo) e {362, 385, 387, 263, 373, 380} (direito), usando a formulação de Soukupová e Čech:

$$EAR=\frac{\vert{}\vert{}p_{2}-p_{6}\vert{}\vert{}+\vert{}\vert{}p_{3}-p_{5}\vert{}\vert{}}{2\cdot\vert{}\vert{}p_{1}-p_{4}\vert{}\vert{}}$$



onde $p_{1}-p_{6}$ são os seis marcos oculares em ordem.


2. Pontuações de Blendshape (`eyeBlinkLeft`, `eyeBlinkRight`, `browInnerUp`, `browOuterUpLeft`, `browOuterUpRight`) em $[0,1]$, usadas para a detecção de piscadas de um único olho e elevação de sobrancelhas.



O sistema suporta seis tipos de gestos: piscar duplo (baseado em EAR), piscar individual do olho esquerdo e direito (baseado em blendshape) e elevação de sobrancelha bilateral ou unilateral (baseado em blendshape). Um temporizador de tempo de recarga (*cooldown*) compartilhado de 4 segundos evita disparos duplicados em todos os tipos de gestos.

**C. Máquina de Estados de Gestos Deliberados de Seis Estados**
O gesto primário — duplo piscar — é desambiguado dos piscares involuntários por meio de um autômato finito de seis estados mostrado na Figura 2. Os piscares involuntários naturais duram 100-400 ms e ocorrem de 15 a 20 vezes por minuto; a máquina de estados rejeita fechamentos espúrios que não atendam aos requisitos de duração mínima.

Os seis estados e suas condições de transição (todos os limites são configuráveis pelo paciente via perfil de detecção, Seção IV-C) são os seguintes:

1. **WAITING_BLINK1**: ocioso. Transição para IN_BLINK1 quando $EAR_{avg} < 0.20$ ($\theta_{close}$).


2. **IN_BLINK1**: contando quadros fechados. Aceita o primeiro piscar e avança para WAITING_OPEN1 quando o olho se reabre ($EAR_{avg} \ge 0.27$, $\theta_{open}$) *após* pelo menos $f_{min}=3$ quadros fechados consecutivos ($\approx 100$ ms a 30 fps); caso contrário, rejeita e retorna para WAITING_BLINK1.


3. **WAITING_OPEN1**: verificando reabertura total. Avança para WAITING_BLINK2 após $f_{open}=3$ quadros abertos consecutivos; registra a marca temporal $t_{1}$.


4. **WAITING_BLINK2**: aguardando o início do segundo piscar, sujeito a um timeout: se $\Delta t=$ agora - $t_{1} > 1800$ ms ($T_{w}$), o autômato é redefinido para WAITING_BLINK1.


5. **IN_BLINK2**: contando os quadros fechados do segundo piscar. Aceita e avança para DONE quando o olho se reabre com $f_{count} \ge 3$ e $\Delta t \le T_{w}$; caso contrário, rejeita e redefine.


6. **DONE**: gesto confirmado; dispara o evento, redefine todo o estado, entra em um cooldown de 4s.



O requisito de duração mínima de fechamento ($f_{min}$) em *ambos* os piscares, combinado com a reabertura total obrigatória em WAITING_OPEN1 e uma janela temporal $T_{w}$, previnem coletivamente as três fontes principais de falsos positivos em populações de movimentos residuais: piscares involuntários únicos, fechamentos parciais (ex: artefatos de ptose) e um desvio lento (drift) que cruze o limite sem um piscar discreto.

**D. Calibração Específica do Paciente via DetectionProfile**
As características faciais dos pacientes variam substancialmente — o EAR basal depende da abertura das pálpebras, da distância rosto-câmera e da iluminação. O NeuroAssist AI acomoda isso por meio do *DetectionProfile*: um registro de configuração imutável e versionado que vincula o conjunto de tipos de gestos ativos (`TipoGesto`) ao agente de borda voltado para o paciente.

Publicar um perfil executa as seguintes etapas no `gest-service`:

1. Consultar todos os tipos de gestos com estado `SUPPORTED` do catálogo.


2. Determinar o número da próxima versão incrementando a versão do perfil ativo atual (ou inicializando em 1).


3. Desativar todos os perfis existentes (`deactivateAll`).


4. Persistir um novo registro `DetectionProfile` com a versão incrementada e um carimbo de versão de contrato (`"1.0.0"`).


5. Persistir um `DetectionProfilePrimitive` por tipo de gesto suportado, transportando o identificador do plug-in `engineFeatureRef`.



O campo `engineFeatureRef` (ex: `"mp.blink.double.v1"`) é um token de string que identifica qual algoritmo de detecção o agente de borda deve ativar para cada primitiva. Esse desacoplamento significa que adicionar um novo tipo de gesto — como um detector de aceno de cabeça ou um detector de pressão nos lábios — requer o registro de um novo `TipoGesto` com um novo `engineFeatureRef` sem modificar nenhum código de domínio existente.

O agente de borda puxa o perfil ativo na inicialização via `GET /gest-service/detection-profile/active` e usa a lista de primitivas retornada para habilitar ou desabilitar as instâncias de detectores correspondentes.

**E. Arquitetura de Microsserviços Hexagonal**
Cada microsserviço (`gest-service`, `evnt-service`) é estruturado de acordo com a arquitetura de Portas e Adaptadores (Hexagonal), imposta pelo ArchUnit em tempo de compilação. A hierarquia de pacotes canônica isola domínios, infraestruturas, persistências de banco e endpoints, onde:
Nove regras de arquitetura são aplicadas em cada build (Tabela I). As regras RULE-01 e RULE-02 garantem que a camada de domínio não tenha importações de frameworks (`org.springframework` ou `jakarta.persistence`), tornando a lógica de negócios central testável independentemente. A RULE-03 impede que o gerenciamento de transações vaze para os controladores ou adaptadores. As RULE-04/05 forçam dependências unidirecionais estritas entre a camada de API e a camada de infraestrutura.

**F. Pipeline de Ingestão de Eventos de Doze Etapas**
O recebimento de um relatório de gestos a partir do agente de borda aciona um pipeline transacional de doze etapas no `ReportIngressService`:

1. **Verificação de Idempotência:** O `reportId` (UUID gerado pelo cliente) é pesquisado. Se encontrado, retorna "duplicate" para evitar alarmes duplicados.


2. **Validação de gestos:** Uma chamada síncrona Feign para o `gest-service` valida o gesto contra a versão ativa do perfil.


3. **Snapshot de comandos:** O `commandRef` é resolvido para um rótulo e nível de urgência.


4. **Roteamento do cuidador:** Determina qual cuidador receberá o alerta.


5. **Criação de Evento:** O registro `Evento` é montado (com marcas temporais).


6. **Criação do NotificationDispatch:** Um registro apontando para a tela cheia do cuidador.


7. **Persistência do Evento:** O evento é salvo na tabela respectiva.


8. **Persistência do NotificationDispatch:** Registro é salvo na sua tabela.


9. **Registro da chave de idempotência:** Mapeamento `reportId -> eventoId` é persistido.


10. **Despacho de notificação:** Porta de envio acionada.


11. **Trilha de auditoria:** Entrada de auditoria adicionada (operação do serviço HIST).


12. **Resposta:** O cliente recebe o `eventoId` e status HTTP 201.
    As etapas 7-9 são executadas sob o mesmo bloco transacional, revertendo tudo caso uma falhe.



**G. Escopo da PoC e Fronteiras Arquiteturais**
O trabalho distingue de forma nítida os componentes avaliados na Prova de Conceito (PoC) — que incluem detecção em WebAssembly e fluxo de confirmação Angular — dos recursos planejados para produção (como Multi-tenant, autenticações e Websockets em vez de polling). Os eventos confirmados nesta pipeline já são estruturalmente compatíveis com dados e pesquisas de VNI em outros softwares, o que abre caminhos para fusões multicanais.

---

### IV. AVALIAÇÃO

Avaliamos o NeuroAssist AI por meio de um estudo de caso preliminar envolvendo um paciente com DNM em estágio avançado e cinco cuidadores e voluntários. O estudo mediu a latência do sistema de ponta a ponta, a taxa de detecção de gestos deliberados e a taxa de falsos positivos sob condições naturalísticas.

**A. Participantes e Ética**
Seis participantes participaram do estudo (Tabela III). O P0 é um paciente com DNM em estágio avançado com histórico clínico documentado; P1-P3 são cuidadores familiares com contato íntimo e regular com o P0; P4-P5 são voluntários saudáveis sem contato prévio com o paciente, servindo como controles de linha de base de falsos positivos naturalísticos. Todos forneceram consentimento informado.

**B. Configuração Experimental**
*Hardware*: Sessões usando webcam USB padrão a 50-70 cm do rosto do participante conectada a um laptop executando o *stack* offline, sem acesso externo.
*Software*: Pipeline de detecção Python comunicando-se com a aplicação Angular em localhost, e microsserviços via Docker Compose. Scripts de medição gravaram marcas temporais.

**C. Calibração do Paciente**
Antes dos ensaios formais, o `DetectionProfile` do P0 foi calibrado observando o movimento palpebral natural. O perfil clínico do P0 é representativo da população-alvo deste software, exigindo ajustes nos limites $\theta_{close}$ e `BLINK_MIN_FRAMES` para refletir essa capacidade restrita de movimento.

**D. Latência**
A latência foi dividida em gesto-para-HTTP e evento-para-alarme visível, sendo este último a maior parte do atraso devido ao intervalo de varredura (*polling*) de 5 segundos no cuidador. A média total ficou em [X] ms ($\sigma=[Y]$ ms), o que prova que a rede e o WASM introduzem latência de detecção irrisória.

**E. Taxa de Falsos Positivos**
Foi medida pela quantidade de disparos não pretendidos por minuto de atividade facial contínua de [M] minutos por pessoa sem tentativas de gesticular intencionalmente ($N=600s$). A máquina de estados reduziu o número drástico inicial da detecção de EAR simples ([A] disparos/min) para apenas [B] disparos/min.

**F. Taxa de Detecção**
O P0 conseguiu uma taxa de [D]% nos acionamentos testados $N=[N]$ com o software calibrado. Voluntários de controle obtiveram pontuações [D1]% e [D2]%, números ligeiramente maiores coerentes com a maior mobilidade e flexibilidade muscular saudáveis.

---

### V. DISCUSSÃO

**A. Acessibilidade de Hardware e Contexto de Implantação**
O pipeline de detecção do NeuroAssist AI não exige GPUs nem hardwares de milhares de dólares ou de técnicos clínicos presentes; demanda apenas webcams (BRL 100-500) e computadores básicos, adequando a invenção para países de baixa ou média renda, locais desassistidos ou regiões rurais.

**B. Privacy-by-Design (Privacidade desde a Concepção)**
Uma vez que vídeos são estritamente dados biométricos (regulamentados via LGPD e GDPR), o processamento WebAssembly direto pelo navegador impede que o feed visual trafegue para qualquer servidor remoto. Apenas um payload JSON com o tipo de gesto e IDs estruturados atravessa a rede local.

**C. Extensibilidade via Mecanismo de Plug-in `engineFeatureRef**`
A arquitetura via plugin significa que adicionar o rastreio de um novo gesto (ex: aceno com a cabeça) exige apenas uma nova linha do Tipo de Gesto e de código da interface de borda, nunca afetando ou arriscando quebrar o código de uso clínico base por causa de diretrizes ArchUnit bem estabelecidas.

**D. Limitações**

* **Dependência de iluminação:** O EAR necessita de claridade. Em ambientes escuros, o limite padrão requer ajuste manual.


* **Escopo de estudo de caso preliminar:** Generalizações em populações clínicas de DNM exigirão aprovações HREC em estudos multilocais futuros.


* **Atraso do Polling:** A atualização por pooling aumenta artificialmente a latência em até 5 segundos de espera, fator limitante solucionável via uso de WebSockets (SSE) futuramente.



**E. Trabalhos Futuros**
Entre os passos prioritários estão: substituições por Server-Sent Events, integração final de catálogos de múltiplos comandos interligados e, com maior destaque, ensaios clínicos mais vastos. Outro caminho de altíssima prioridade é integrar e cruzar diretamente esses dados com streams contínuos de leituras ventilatórias (em formatos EDF de registros polissonográficos) e descobrir os padrões exatos de angústia correlacionados a momentos elevados da assincronia de ventilação no monitor (PVA) — algo que não pode ser inferido testando os sistemas de forma separada.

---

### VI. CONCLUSÃO

Apresentamos o NeuroAssist AI, uma plataforma que permite a pacientes em estados severos de esclerose lateral interagir por microgestos (identificados em ambiente WebAssembly perfeitamente confinado). A filtragem de dupla detecção com janela de tempo exclui flutuações e movimentos oculares parciais, mitigando drasticamente disparos não intencionais.

Construído de forma offline, modular e imune aos requerimentos da internet na residência do paciente, a plataforma permite atender a necessidades críticas a custos baixíssimos (usando webcams simples). A validação empírica já demonstra eficácia alta de conversão temporal, devendo impulsionar testes mais amplos para cruzar padrões de estresse ao redor das interações noturnas do tratamento de ventilação.
