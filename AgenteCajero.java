import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.TickerBehaviour;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class AgenteCajero extends Agent {

    private Map<Integer, Plato> menu = new HashMap<>();
    private boolean cajaBloqueada = false;
    private List<ACLMessage> mensajesPendientes = new ArrayList<>();

    @Override
    protected void setup() {
        printAnimated("Cajero iniciado: " + getLocalName());
        registrarServicio();
        cargarMenu();
        addBehaviour(new ComportamientoCajero(this, 750));
    }

    private void printAnimated(String mensaje) {
        System.out.print("[Cajero] >> " + mensaje);
        System.out.println();
    }

    private void printAlert(String mensaje) {
        String linea = new String(new char[mensaje.length() + 4]).replace('\0', '-');
        System.out.println("\n" + linea);
        System.out.print("| " + mensaje + " |");
        System.out.println("\n" + linea + "\n");
    }

    private void registrarServicio() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("servicio-caja");
        sd.setName("Caja-Restaurante");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) { e.printStackTrace(); }
    }

    private void cargarMenu() {
        try (BufferedReader br = new BufferedReader(new FileReader("menu.txt"))) {
            String linea; br.readLine(); 
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(";");
                menu.put(Integer.parseInt(partes[0]), new Plato(Integer.parseInt(partes[0]), partes[1], Double.parseDouble(partes[2])));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private class ComportamientoCajero extends TickerBehaviour {

        public ComportamientoCajero(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (cajaBloqueada) {
                // --- ESTADO: BLOQUEADO ---
                MessageTemplate unlockTemplate = MessageTemplate.MatchContent("CAJA_DESBLOQUEADA");
                ACLMessage unlockMsg = myAgent.receive(unlockTemplate);

                if (unlockMsg != null) {
                    cajaBloqueada = false;
                    printAnimated("Caja desbloqueada. Procesando " + mensajesPendientes.size() + " mensajes pendientes...");
                    procesarPendientes();
                } else {
                    ACLMessage otherMsg = myAgent.receive();
                    if (otherMsg != null) {
                        printAnimated("Caja bloqueada. Guardando mensaje '" + otherMsg.getContent() + "' para más tarde.");
                        mensajesPendientes.add(otherMsg);
                    }
                }
            } else {
                // --- ESTADO: DESBLOQUEADO ---
                if (!mensajesPendientes.isEmpty()) {
                    procesarPendientes();
                }

                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    procesarMensajeNormal(msg);
                }
            }
        }

        private void procesarPendientes() {
            if (mensajesPendientes.isEmpty()) return;
            printAnimated("Resumiendo " + mensajesPendientes.size() + " operaciones pendientes.");
            java.util.Iterator<ACLMessage> it = mensajesPendientes.iterator();
            while(it.hasNext()){
                procesarMensajeNormal(it.next());
                it.remove();
            }
        }

        private void procesarMensajeNormal(ACLMessage msg) {
            String contenido = msg.getContent();
            if (contenido.equals("ASALTO_EN_CURSO")) {
                cajaBloqueada = true;
                printAlert("!!! ASALTO - Bloqueando caja y guardando estado !!!");
                ACLMessage alerta = new ACLMessage(ACLMessage.INFORM);
                AID mesero = buscarMesero();
                if (mesero != null) {
                    alerta.addReceiver(mesero);
                    alerta.setContent("EMERGENCIA_ASALTO");
                    send(alerta);
                }
            } else if (contenido.startsWith("COBRAR_ID=")) {
                try {
                    int id = Integer.parseInt(contenido.split("=")[1]);
                    printAnimated("Procesando cobro: " + id);
                    if (menu.containsKey(id)) {
                        Plato p = menu.get(id);
                        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                        reply.addReceiver(msg.getSender());
                        reply.setContent("BOLETA;" + p.id + ";" + p.nombre + ";" + p.precio);
                        send(reply);
                        printAnimated("Boleta enviada a " + msg.getSender().getLocalName());
                    }
                } catch (Exception e) { e.printStackTrace(); }
            } else if (contenido.equals("CAJA_DESBLOQUEADA")) {
                cajaBloqueada = false;
                printAnimated("Info: La caja ya estaba desbloqueada.");
            } else {
                printAnimated("Mensaje ignorado: " + contenido);
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
        } catch (FIPAException e) { }
        return null;
    }

    private static class Plato {
        int id; String nombre; double precio;
        Plato(int id, String nombre, double precio) {
            this.id = id; this.nombre = nombre; this.precio = precio;
        }
    }
}
