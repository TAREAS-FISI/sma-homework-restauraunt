
import jade.core.Agent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.CyclicBehaviour;

public class AgenteMesero extends Agent {

    private AID clienteActual;

    @Override
    protected void setup() {
        System.out.println("Mesero iniciado: " + getLocalName());

        registrarServicio();
        addBehaviour(new ComportamientoMesero());
    }

    // ================= REGISTRO DF =================
    private void registrarServicio() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("servicio-mesero");
        sd.setName("Mesero-Restaurante");

        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println("Mesero registrado en DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    // ================= COMPORTAMIENTO =================
    private class ComportamientoMesero extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) {
                block();
                return;
            }

            String contenido = msg.getContent();

            // -------- PEDIDO DEL CLIENTE --------
            if (contenido.startsWith("PEDIDO_ID=")) {
                clienteActual = msg.getSender();
                System.out.println("Pedido recibido del cliente: " + contenido);

                AID cocinero = buscarAgente("servicio-cocina");
                if (cocinero != null) {
                    reenviarPedido(cocinero, contenido);
                } else {
                    informarCliente("No hay cocinero disponible");
                }
            }

            // -------- RESPUESTA DE COCINA --------
            else if (contenido.startsWith("PLATO_LISTO")) {
                System.out.println("🍳 Cocina confirmó: " + contenido);

                String[] partes = contenido.split(";");
                String idPlatillo = partes[1];
                String nombrePlatillo = partes[2];

                // PASO 1: Informar al cliente que su comida está servida.
                informarCliente("Aquí tiene su plato: " + nombrePlatillo);
                System.out.println("Plato servido al cliente.");

                // PASO 2: Ahora, pedir la cuenta a caja.
                AID cajero = buscarAgente("servicio-caja");
                if (cajero != null) {
                    enviarACaja(cajero, idPlatillo);
                } else {
                    informarCliente("No hay cajero disponible para generar su cuenta.");
                }
            }

            else if (contenido.startsWith("PLATO_NO_DISPONIBLE")) {
                System.out.println("Cocina rechazó el pedido");
                informarCliente("Plato no disponible");
            }

            // -------- BOLETA DESDE CAJA --------
            else if (contenido.startsWith("BOLETA")) {
                System.out.println("Boleta recibida desde caja");
                reenviarBoletaAlCliente(contenido);
            }

            // -------- EMERGENCIA --------
            else if (contenido.equals("EMERGENCIA_ASALTO")) {
                System.out.println("EMERGENCIA DE ASALTO");

                AID policia = buscarAgente("servicio-policia");
                if (policia != null) {
                    ACLMessage alerta = new ACLMessage(ACLMessage.REQUEST);
                    alerta.addReceiver(policia);
                    alerta.setContent("INTERVENCION_ASALTO");
                    send(alerta);

                    System.out.println("Policía solicitada");
                } else {
                    System.out.println("No se encontró policía");
                }
            }

            else if (msg.getContent().equals("LADRON_ARRESTADO")) {
                System.out.println("Policía confirmó arresto");

                ACLMessage avisoCajero = new ACLMessage(ACLMessage.INFORM);
                avisoCajero.setContent("CAJA_DESBLOQUEADA");
                avisoCajero.addReceiver(buscarAgente("servicio-cajero"));

                send(avisoCajero);

                System.out.println("Caja habilitada nuevamente");
            }
        }
    }

    // ================= MÉTODOS AUXILIARES =================
    private AID buscarAgente(String tipoServicio) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(tipoServicio);
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

    private void reenviarPedido(AID cocinero, String pedido) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(cocinero);
        msg.setContent(pedido);
        send(msg);
        System.out.println("Pedido enviado a cocina");
    }

    private void enviarACaja(AID cajero, String idPlatillo) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(cajero);
        msg.setContent("COBRAR_ID=" + idPlatillo);
        send(msg);
        System.out.println("Pedido enviado a caja");
    }

    private void reenviarBoletaAlCliente(String boleta) {
        if (clienteActual != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(clienteActual);
            msg.setContent(boleta);
            msg.setConversationId("recibir-boleta"); // CLAVE: Añadir ID de conversación
            send(msg);
            System.out.println("Boleta enviada al cliente");
        }
    }

    private void informarCliente(String mensaje) {
        if (clienteActual != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(clienteActual);
            msg.setContent(mensaje);
            msg.setConversationId("pedido-comida"); // CLAVE: Añadir ID de conversación
            send(msg);
        }
    }
}
