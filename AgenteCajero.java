import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.TickerBehaviour; // Cambio a TickerBehaviour

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
        System.out.println("Cajero iniciado: " + getLocalName());
        registrarServicio();
        cargarMenu();
        // Se añade un TickerBehaviour que se ejecuta cada 750 ms
        addBehaviour(new ComportamientoCajero(this, 750));
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
                    System.out.println("Caja desbloqueada. Procesando " + mensajesPendientes.size() + " mensajes pendientes...");
                    procesarPendientes();
                } else {
                    ACLMessage otherMsg = myAgent.receive();
                    if (otherMsg != null) {
                        System.out.println("Caja bloqueada. Guardando mensaje '" + otherMsg.getContent() + "' para más tarde.");
                        mensajesPendientes.add(otherMsg);
                    }
                    // No hay block(), el Ticker nos despertará de nuevo.
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
                // No hay block(), el Ticker nos despertará de nuevo.
            }
        }

        private void procesarPendientes() {
            if (mensajesPendientes.isEmpty()) return;
            System.out.println("Resumiendo " + mensajesPendientes.size() + " operaciones pendientes.");
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
                System.out.println("!!! ASALTO - Bloqueando caja y guardando estado !!!");
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
                    System.out.println("Procesando cobro: " + id);
                    if (menu.containsKey(id)) {
                        Plato p = menu.get(id);
                        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                        reply.addReceiver(msg.getSender());
                        reply.setContent("BOLETA;" + p.id + ";" + p.nombre + ";" + p.precio);
                        send(reply);
                        System.out.println("-> Boleta enviada a " + msg.getSender().getLocalName());
                    }
                } catch (Exception e) { e.printStackTrace(); }
            } else if (contenido.equals("CAJA_DESBLOQUEADA")) {
                cajaBloqueada = false;
                System.out.println("Info: La caja ya estaba desbloqueada.");
            } else {
                System.out.println("Mensaje ignorado: " + contenido);
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
