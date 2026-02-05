import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.CyclicBehaviour;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList; // Importante
import java.util.List;      // Importante

public class AgenteCajero extends Agent {

    private Map<Integer, Plato> menu = new HashMap<>();
    private boolean cajaBloqueada = false;
    // Buffer para guardar mensajes que llegan durante el asalto
    private List<ACLMessage> mensajesPendientes = new ArrayList<>();

    @Override
    protected void setup() {
        System.out.println("Cajero iniciado: " + getLocalName());
        registrarServicio();
        cargarMenu();
        addBehaviour(new ComportamientoCajero());
    }

    // ... (Métodos registrarServicio y cargarMenu se mantienen IGUAL) ...
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

    private class ComportamientoCajero extends CyclicBehaviour {

        @Override
        public void action() {
            // Recibimos TODO. No usamos templates para no dejar nada "escondido" en la cola de JADE.
            ACLMessage msg = myAgent.receive();

            if (msg == null) {
                block(); 
                return;
            }

            String contenido = msg.getContent();

            // --- LÓGICA DE ESTADO BLOQUEADO ---
            if (cajaBloqueada) {
                if (contenido.equals("CAJA_DESBLOQUEADA")) {
                    System.out.println(">>> CAJA DESBLOQUEADA. Procesando pendientes...");
                    cajaBloqueada = false;
                    
                    // CRÍTICO: Procesar todo lo que acumulamos mientras estábamos bloqueados
                    procesarPendientes(); 
                } 
                else {
                    // Si no es el desbloqueo, lo guardamos para luego.
                    // No lo procesamos, pero lo SACAMOS de la cola de JADE para evitar bloqueos.
                    System.out.println("(Caja Bloqueada) Mensaje guardado en espera: " + contenido);
                    mensajesPendientes.add(msg);
                }
                return; // Terminamos esta vuelta
            }

            // --- LÓGICA DE ESTADO NORMAL ---
            procesarMensajeNormal(msg);
        }

        private void procesarPendientes() {
            if (mensajesPendientes.isEmpty()) return;

            System.out.println("Resumiendo " + mensajesPendientes.size() + " operaciones pendientes.");
            for (ACLMessage msgPendiente : mensajesPendientes) {
                // Reutilizamos la lógica normal para cada mensaje guardado
                procesarMensajeNormal(msgPendiente);
            }
            mensajesPendientes.clear(); // Limpiamos el buffer
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