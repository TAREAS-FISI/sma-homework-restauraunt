import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.CyclicBehaviour;
import java.util.Random;

public class AgenteCliente extends Agent {

    private static final long TIEMPO_COMER_MS = 5000;
    private static final long TIEMPO_ESPERA_REORDENAR_MS = 10000;
    private static final int TIEMPO_BUSQUEDA_MESERO_MS = 2000;

    @Override
    protected void setup() {
        System.out.println("Cliente listo: " + getLocalName());
        addBehaviour(new ComportamientoCiclicoCliente());
    }

    private class ComportamientoCiclicoCliente extends CyclicBehaviour {
        // ... (Constantes de estado igual que antes) ...
        private static final int STATE_INICIAR_NUEVO_PEDIDO = 0;
        private static final int STATE_ESPERAR_COMIDA = 1;
        private static final int STATE_COMER = 2;
        private static final int STATE_ESPERAR_BOLETA = 3;
        private static final int STATE_PAGAR_Y_REINICIAR = 4;
        private static final int STATE_BUSCANDO_MESERO = 5;

        private int state = STATE_BUSCANDO_MESERO;
        private long timerStart;
        private MessageTemplate templateComida;
        private MessageTemplate templateBoleta;

        @Override
        public void action() {
            switch (state) {
                case STATE_BUSCANDO_MESERO:
                    System.out.println(getLocalName() + " buscará mesero...");
                    block(TIEMPO_BUSQUEDA_MESERO_MS);
                    state = STATE_INICIAR_NUEVO_PEDIDO;
                    break;

                case STATE_INICIAR_NUEVO_PEDIDO:
                    // ... (Esta parte estaba bien, la dejo resumida) ...
                    AID mesero = buscarMesero();
                    if (mesero != null) {
                        int pedidoId = new Random().nextInt(5) + 1; // Ajustado rango para pruebas
                        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                        msg.addReceiver(mesero);
                        msg.setContent("PEDIDO_ID=" + pedidoId);
                        msg.setConversationId("pedido-comida");
                        send(msg);
                        System.out.println(getLocalName() + " pidió plato ID: " + pedidoId);
                        
                        // Preparar templates
                        templateComida = MessageTemplate.and(
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                            MessageTemplate.MatchConversationId("pedido-comida")
                        );
                        state = STATE_ESPERAR_COMIDA;
                    } else {
                        block(2000); // Esperar antes de reintentar
                    }
                    break;

                case STATE_ESPERAR_COMIDA:
                    ACLMessage msgComida = myAgent.receive(templateComida);
                    if (msgComida != null) {
                        System.out.println(getLocalName() + " -> Comida recibida.");
                        state = STATE_COMER;
                    } else {
                        block();
                    }
                    break;

                case STATE_COMER:
                    System.out.println(getLocalName() + " está comiendo (Ñam ñam)...");
                    timerStart = System.currentTimeMillis();
                    
                    // Configuramos el template de la boleta AQUÍ para estar listos
                    templateBoleta = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.MatchConversationId("recibir-boleta")
                    );
                    
                    state = STATE_ESPERAR_BOLETA;
                    // No hacemos block aquí, dejamos que el flujo caiga al siguiente estado
                    // o que el ciclo de JADE lo maneje.
                    break;

                case STATE_ESPERAR_BOLETA:
                    // 1. Calcular cuánto tiempo ha pasado
                    long tiempoPasado = System.currentTimeMillis() - timerStart;
                    long tiempoRestante = TIEMPO_COMER_MS - tiempoPasado;

                    if (tiempoRestante > 0) {
                        // Aún no terminamos de comer.
                        // Bloqueamos SOLO por el tiempo restante.
                        // Si llega un mensaje, despertaremos, veremos que falta tiempo
                        // y volveremos a dormir el resto.
                        block(tiempoRestante);
                        return; 
                    }

                    // 2. Si llegamos aquí, ya terminamos de comer. Buscamos la boleta.
                    System.out.println(getLocalName() + " terminó de comer. Esperando cuenta...");
                    
                    ACLMessage msgBoleta = myAgent.receive(templateBoleta);
                    if (msgBoleta != null) {
                        System.out.println(getLocalName() + " PAGANDO: " + msgBoleta.getContent());
                        state = STATE_PAGAR_Y_REINICIAR;
                    } else {
                        // Si el tiempo pasó pero la boleta aun no llega (raro, pero posible)
                        // Bloqueamos indefinidamente hasta que llegue.
                        block();
                    }
                    break;

                case STATE_PAGAR_Y_REINICIAR:
                    System.out.println(getLocalName() + " se va contento. Volverá pronto.");
                    block(TIEMPO_ESPERA_REORDENAR_MS);
                    state = STATE_INICIAR_NUEVO_PEDIDO;
                    break;
            }
        }
    }

    // ... (El método buscarMesero se mantiene igual) ...
    private AID buscarMesero() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("servicio-mesero");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return result[0].getName();
        } catch (FIPAException e) { e.printStackTrace(); }
        return null;
    }
}