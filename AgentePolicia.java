

import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;

public class AgentePolicia extends Agent {

    @Override
    protected void setup() {
        System.out.println("👮 Policía activo: " + getLocalName());

        registrarServicio();
        addBehaviour(new ComportamientoPolicia());
    }

    // ================= REGISTRO DF =================
    private void registrarServicio() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("servicio-policia");
        sd.setName("Policia-Restaurante");

        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("✅ Policía registrado en DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    // ================= COMPORTAMIENTO =================
    private class ComportamientoPolicia extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) {
                block();
                return;
            }

            if (msg.getContent().equals("INTERVENCION_ASALTO")) {
                System.out.println("🚓 Intervención solicitada");
                System.out.println("🚨 Policía en camino...");

                final ACLMessage alertaMsg = msg;

                // Usar WakerBehaviour para simular el tiempo de llegada sin bloquear al agente
                addBehaviour(new WakerBehaviour(myAgent, 5000) {
                    @Override
                    protected void onWake() {
                        ACLMessage respuesta = new ACLMessage(ACLMessage.INFORM);
                        respuesta.addReceiver(alertaMsg.getSender());
                        respuesta.setContent("LADRON_ARRESTADO");
                        send(respuesta);
                        System.out.println("👮‍♂️ LADRÓN ARRESTADO - Zona segura");
                    }
                });
            }
        }
    }
}
