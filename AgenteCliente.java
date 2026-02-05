

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.WakerBehaviour;

import java.util.Random;

public class AgenteCliente extends Agent {

    private static final int TIEMPO_COMER = 5000; // 5 segundos

    @Override
    protected void setup() {
        System.out.println("🧑‍🍽️ Cliente iniciado: " + getLocalName());

        SequentialBehaviour seq = new SequentialBehaviour();
        addBehaviour(seq);

        // 1. Ordenar
        seq.addSubBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                AID mesero = buscarMesero();
                if (mesero != null) {
                    int pedidoId = new Random().nextInt(12) + 1;
                    System.out.println("🎲 Cliente " + getLocalName() + " eligió platillo con ID: " + pedidoId);

                    ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                    msg.addReceiver(mesero);
                    msg.setContent("PEDIDO_ID=" + pedidoId);
                    send(msg);
                    System.out.println("📤 " + getLocalName() + " envió su pedido al mesero.");
                } else {
                    System.out.println("❌ " + getLocalName() + " no encontró al Agente Mesero y se irá.");
                    myAgent.doDelete();
                }
            }
        });

        // 2. Esperar comida o rechazo
        seq.addSubBehaviour(new Behaviour() {
            private boolean done = false;

            @Override
            public void action() {
                ACLMessage msg = myAgent.receive(jade.lang.acl.MessageTemplate.MatchPerformative(ACLMessage.INFORM));
                if (msg != null) {
                    if (msg.getContent().startsWith("✅ Aquí tiene su plato:")) {
                        System.out.println("📩 " + getLocalName() + " recibió: " + msg.getContent());
                        done = true; // Comida recibida, pasar a comer
                    } else if (msg.getContent().contains("Plato no disponible")) {
                        System.out.println("📩 " + getLocalName() + " recibió: " + msg.getContent() + ". Se irá molesto.");
                        myAgent.doDelete(); // El cliente se va
                    }
                } else {
                    block();
                }
            }

            @Override
            public boolean done() {
                if (done) {
                    return true;
                } else {
                    // Si el agente se está eliminando porque el plato no estaba disponible
                    return getAgent().getCurState().getCode() == jade.core.AgentState.AP_DELETED;
                }
            }
        });

        // 3. Comer
        seq.addSubBehaviour(new WakerBehaviour(this, TIEMPO_COMER) {
            @Override
            protected void onWake() {
                System.out.println("😋 " + getLocalName() + " ha terminado de comer.");
            }
        });

        // 4. Esperar boleta
        seq.addSubBehaviour(new Behaviour() {
            private boolean boletaRecibida = false;

            @Override
            public void action() {
                // Espera un mensaje que empiece con BOLETA
                ACLMessage msg = myAgent.receive(jade.lang.acl.MessageTemplate.MatchPattern.create("^BOLETA.*"));
                if (msg != null) {
                    System.out.println("📩 " + getLocalName() + " recibió la boleta: " + msg.getContent());
                    boletaRecibida = true;
                } else {
                    block();
                }
            }

            @Override
            public boolean done() {
                return boletaRecibida;
            }
        });

        // 5. Irse
        seq.addSubBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                System.out.println("👋 " + getLocalName() + " pagó y se va satisfecho.");
                myAgent.doDelete();
            }
        });
    }

    private AID buscarMesero() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("servicio-mesero");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                System.out.println("✅ " + getLocalName() + " encontró al Mesero");
                return result[0].getName();
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return null;
    }
}
