# sistDist-socket
Trabalho 1 de Sistemas Distribuidos

Enunciado:
Algoritmo de Eleição
Desenvolva uma aplicação que eleja de forma consensual um líder/coordenador
entre quatro processos que fazem parte de um grupo multicast.
Utilize o algoritmo de Bully (Valentão) visto na aula 5. Esse algoritmo assume
que um processo sabe o ID de todos os outros processos no sistema.
Considere quatro tipos de mensagens:
 Mensagem de Eleição - Unicast;
 Mensagem de Resposta à uma mensagem de eleição (Processo ativo) -
Unicast;
 Mensagem de Coordenador para anunciar ID do processo eleito -
Multicast;
 Mensagem “Olá” do coordenador para indicar que está ativo - Multicast.
 Um processo Pi inicia uma eleição quando detecta que o coordenador
falhou (isto é, quando detecta a ausência da mensagem “Olá” do
coordenador_eleito). Vários processos podem detectar isso
simultaneamente.
 O processo que sabe que possui o ID mais alto pode eleger a si mesmo
como coordenador.
o Envia mensagem de coordenador (multicast) a todos os
processos. A partir desse momento passa a enviar mensagens de
“Olá” a cada ∆t1.

 Por outro lado, um processo Pi com ID mais baixo inicia uma eleição
enviando uma mensagem de eleição para os processos com ID mais alto
e espera uma resposta por um tempo ∆t2.
o Se nenhuma resposta chegar dentro desse tempo ∆t2, o processo
Pi se considerará o coordenador e enviará uma mensagem de
coordenador (multicast) a todos os processos. A partir desse
momento passa a enviar mensagens de “Olá” ∆t1;
o Se uma resposta chegar, o processo Pi vai esperar por mais um
período ∆t3 para que uma mensagem de coordenador chegue. Se
nenhuma mensagem de coordenador chegar, o processo Pi
iniciará uma nova eleição.

 Se um processo recebe uma mensagem de eleição, ele envia de volta
uma mensagem de resposta (unicast) e inicia outra eleição, a menos
que já tenha iniciado uma. Essa mensagem de resposta a uma eleição
serve apenas para indicar que o processo está ativo.
 Se um processo Pi recebe uma mensagem de coordenador, ele
configura sua variável coordenador_eleito com o ID do processo
coordenador contido nessa mensagem. O processo Pi monitorará o
estado (ativo ou inativo) do coordenador_eleito.

Observações:
• Desenvolva uma interface com recursos de interação apropriados;
• É obrigatória a defesa da aplicação para obter a nota.
• O desenvolvimento da aplicação pode ser individual ou em dupla. Porém,
a defesa da aplicação é individual.
