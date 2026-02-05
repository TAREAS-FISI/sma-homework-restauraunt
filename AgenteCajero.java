
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

public class AgenteCajero extends Agent {

    private Map<Integer, Plato> menu = new HashMap<>();
    private boolean cajaBloqueada = false;

    @Override
    protected void setup() {
        System.out.println("💵 Cajero iniciado: " + getLocalName());

        registrarServicio();
        cargarMenu();

        addBehaviour(new ComportamientoCajero());
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
            System.out.println("✅ Cajero registrado en DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private void cargarMenu() {
        try (BufferedReader br = new BufferedReader(new FileReader("menu.txt"))) {
            String linea;
            br.readLine(); // saltar encabezado

            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(";");
                int id = Integer.parseInt(partes[0]);
                String nombre = partes[1];
                double precio = Double.parseDouble(partes[2]);

                menu.put(id, new Plato(id, nombre, precio));
            }
            System.out.println("📖 Menú cargado en caja");
        } catch (Exception e) {
            System.out.println("❌ Error leyendo menu.txt");
            e.printStackTrace();
        }
    }

    private class ComportamientoCajero extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) {
                block();
                return;
            }

            String contenido = msg.getContent();

            // -------- ASALTO --------
            if (contenido.equals("ASALTO_EN_CURSO")) {
                cajaBloqueada = true;
                System.out.println("🚨 ASALTO DETECTADO - Caja bloqueada");

                ACLMessage alerta = new ACLMessage(ACLMessage.INFORM);
                alerta.addReceiver(buscarMesero());
                alerta.setContent("EMERGENCIA_ASALTO");
                send(alerta);

                block(); // 🔑 CLAVE
                return;
            }

            // -------- DESBLOQUEO --------
            else if (contenido.equals("CAJA_DESBLOQUEADA")) {
                cajaBloqueada = false;
                System.out.println("✅ Caja desbloqueada. Operaciones normales");
                return; // aquí sí está bien, no bloquea
            }

            // -------- INTENTO DE COBRO --------
            if (cajaBloqueada) {
                System.out.println("⛔ Caja bloqueada, esperando desbloqueo...");
                block(); // 🔑 CLAVE
                return;
            }

            // Cobro normal
            if (contenido.startsWith("COBRAR_ID=")) {
                int id = Integer.parseInt(contenido.split("=")[1]);
                System.out.println("📩 Caja recibió cobro ID: " + id);

                if (menu.containsKey(id)) {
                    Plato plato = menu.get(id);

                    ACLMessage boleta = new ACLMessage(ACLMessage.INFORM);
                    boleta.addReceiver(msg.getSender()); // Mesero
                    boleta.setContent(
                            "BOLETA;" + plato.id + ";" +
                                    plato.nombre + ";" +
                                    plato.precio);
                    send(boleta);

                    System.out.println("🧾 Boleta emitida: " + plato.nombre +
                            " S/." + plato.precio);
                }
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
            if (result.length > 0) {
                return result[0].getName();
            }
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Clase interna Plato
    private static class Plato {
        int id;
        String nombre;
        double precio;

        Plato(int id, String nombre, double precio) {
            this.id = id;
            this.nombre = nombre;
            this.precio = precio;
        }
    }
}
