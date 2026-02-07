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
        printAnimated("Cliente listo: " + getLocalName());
        addBehaviour(new ComportamientoCiclicoCliente());
    }

    private void printAnimated(String mensaje) {
        System.out.print("[Cliente] >> " + mensaje);
        System.out.println();
    }

    private void printAlert(String mensaje) {
        String linea = new String(new char[mensaje.length() + 4]).replace('\0', '-');
        System.out.println("\n" + linea);
        System.out.print("| " + mensaje + " |");
        System.out.println("\n" + linea + "\n");
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
                    printAnimated(getAID().getLocalName() + " buscará mesero...");
                    block(TIEMPO_BUSQUEDA_MESERO_MS);
                    state = STATE_INICIAR_NUEVO_PEDIDO;
                    break;

                case STATE_INICIAR_NUEVO_PEDIDO:
                    AID mesero = buscarMesero();
                    if (mesero != null) {
                        int pedidoId = new Random().nextInt(12) + 1;
                        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                        msg.addReceiver(mesero);
                        msg.setContent("PEDIDO_ID=" + pedidoId);
                        msg.setConversationId("pedido-comida");
                        send(msg);
                        printAnimated("Pidió plato ID: " + pedidoId);
                                                
                        templateComida = MessageTemplate.and(
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                            MessageTemplate.MatchConversationId("pedido-comida")
                        );
                        state = STATE_ESPERAR_COMIDA;
                    } else {
                        block(2000);
                    }
                    break;

                case STATE_ESPERAR_COMIDA:
                    ACLMessage msgComida = myAgent.receive(templateComida);
                    if (msgComida != null) {
                        printAnimated("Comida recibida.");
                        state = STATE_COMER;
                    } else {
                        block();
                    }
                    break;

                case STATE_COMER:
                    printAnimated("Está comiendo (Ñam ñam)...");
                    timerStart = System.currentTimeMillis();
                    
                    templateBoleta = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.MatchConversationId("recibir-boleta")
                    );
                    
                    state = STATE_ESPERAR_BOLETA;
                    break;

                case STATE_ESPERAR_BOLETA:
                    long tiempoPasado = System.currentTimeMillis() - timerStart;
                    long tiempoRestante = TIEMPO_COMER_MS - tiempoPasado;

                    if (tiempoRestante > 0) {
                        block(tiempoRestante);
                        return; 
                    }

                    printAnimated("Terminó de comer. Esperando cuenta...");
                    
                    ACLMessage msgBoleta = myAgent.receive(templateBoleta);
                    if (msgBoleta != null) {
                        printAlert("PAGANDO: " + msgBoleta.getContent());
                        state = STATE_PAGAR_Y_REINICIAR;
                    } else {
                        block();
                    }
                    break;

                case STATE_PAGAR_Y_REINICIAR:
                    printAnimated("Se va contento. Volverá pronto.");
                    block(TIEMPO_ESPERA_REORDENAR_MS);
                    state = STATE_INICIAR_NUEVO_PEDIDO;
                    break;
            }
        }
    }

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