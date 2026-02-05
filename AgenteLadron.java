

import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.TickerBehaviour;

import java.util.Random;

public class AgenteLadron extends Agent {

    private static final double PROBABILIDAD_ASALTO = 0.7;
    private static final int INTERVALO = 8000; // ms

    @Override
    protected void setup() {
        System.out.println("😈 Ladrón activo: " + getLocalName());

        addBehaviour(new ComportamientoLadron(this, INTERVALO));
    }

    private class ComportamientoLadron extends TickerBehaviour {

        private Random random = new Random();

        public ComportamientoLadron(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            double valor = random.nextDouble();
            System.out.println("😈 Ladrón evaluando asalto... (" + valor + ")");

            if (valor < PROBABILIDAD_ASALTO) {
                AID cajero = buscarCajero();
                if (cajero != null) {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(cajero);
                    msg.setContent("ASALTO_EN_CURSO");
                    send(msg);

                    System.out.println("🚨 ASALTO INICIADO");
                } else {
                    System.out.println("❌ No se encontró cajero");
                }

                // Opcional: detener al ladrón después del asalto
                stop();
            }
        }
    }

    private AID buscarCajero() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("servicio-caja");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                return result[0].getName();
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return null;
    }
}
