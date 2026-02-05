

import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class AgenteCocinero extends Agent {

    private Map<Integer, Plato> menu = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("Cocinero iniciado: " + getLocalName());

        registrarServicio();
        cargarMenu();

        addBehaviour(new ComportamientoCocinero());
    }

    private void registrarServicio() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("servicio-cocina");
        sd.setName("Cocina-Restaurante");

        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("Cocinero registrado en DF");
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
                int tiempo = Integer.parseInt(partes[3]);

                menu.put(id, new Plato(id, nombre, precio, tiempo));
            }
            System.out.println("Menú cargado en cocina");
        } catch (Exception e) {
            System.out.println("Error leyendo menu.txt");
            e.printStackTrace();
        }
    }

    private class ComportamientoCocinero extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {

                String contenido = msg.getContent();

                if (contenido.startsWith("PEDIDO_ID=")) {
                    int idPedido = Integer.parseInt(contenido.split("=")[1]);
                    System.out.println("Cocina recibió pedido ID: " + idPedido);

                    if (menu.containsKey(idPedido)) {
                        final Plato plato = menu.get(idPedido);
                        final ACLMessage pedidoMsg = msg;

                        System.out.println("Preparando: " + plato.nombre + " (" + plato.tiempoPreparacion + "s)");

                        // Usar WakerBehaviour para simular el tiempo de cocción sin bloquear al agente
                        addBehaviour(new WakerBehaviour(myAgent, plato.tiempoPreparacion * 1000L) {
                            @Override
                            protected void onWake() {
                                ACLMessage respuesta = new ACLMessage(ACLMessage.INFORM);
                                respuesta.addReceiver(pedidoMsg.getSender());
                                respuesta.setContent("PLATO_LISTO;" + plato.id + ";" + plato.nombre);
                                send(respuesta);
                                System.out.println("Plato listo: " + plato.nombre);
                            }
                        });

                    } else {
                        ACLMessage respuesta = new ACLMessage(ACLMessage.INFORM);
                        respuesta.addReceiver(msg.getSender());
                        respuesta.setContent("PLATO_NO_DISPONIBLE;" + idPedido);
                        send(respuesta);

                        System.out.println("Plato no disponible");
                    }
                }
            } else {
                block();
            }
        }
    }

    // Clase interna para representar un plato
    private static class Plato {
        int id;
        String nombre;
        double precio;
        int tiempoPreparacion;

        Plato(int id, String nombre, double precio, int tiempoPreparacion) {
            this.id = id;
            this.nombre = nombre;
            this.precio = precio;
            this.tiempoPreparacion = tiempoPreparacion;
        }
    }
}
