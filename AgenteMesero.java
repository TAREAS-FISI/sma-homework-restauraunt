
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
        printAnimated("Mesero iniciado: " + getLocalName());

        registrarServicio();
        addBehaviour(new ComportamientoMesero());
    }

    private void printAnimated(String mensaje) {
        System.out.print("[Mesero] >> " + mensaje);
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
        sd.setType("servicio-mesero");
        sd.setName("Mesero-Restaurante");

        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            printAnimated("Mesero registrado en DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

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
                printAnimated("Pedido recibido del cliente: " + contenido);

                AID cocinero = buscarAgente("servicio-cocina");
                if (cocinero != null) {
                    reenviarPedido(cocinero, contenido);
                } else {
                    informarCliente("No hay cocinero disponible");
                }
            }

            // -------- RESPUESTA DE COCINA --------
            else if (contenido.startsWith("PLATO_LISTO")) {
                printAnimated("Cocina confirmó: " + contenido);

                String[] partes = contenido.split(";");
                String idPlatillo = partes[1];
                String nombrePlatillo = partes[2];

                informarCliente("Aquí tiene su plato: " + nombrePlatillo);
                printAnimated("Plato servido al cliente.");

                AID cajero = buscarAgente("servicio-caja");
                if (cajero != null) {
                    enviarACaja(cajero, idPlatillo);
                } else {
                    informarCliente("No hay cajero disponible para generar su cuenta.");
                }
            }

            else if (contenido.startsWith("PLATO_NO_DISPONIBLE")) {
                printAnimated("Cocina rechazó el pedido");
                informarCliente("Plato no disponible");
            }

            else if (contenido.startsWith("BOLETA")) {
                printAnimated("Boleta recibida desde caja");
                reenviarBoletaAlCliente(contenido);
            }

            else if (contenido.equals("EMERGENCIA_ASALTO")) {
                printAlert("EMERGENCIA DE ASALTO");

                AID policia = buscarAgente("servicio-policia");
                if (policia != null) {
                    ACLMessage alerta = new ACLMessage(ACLMessage.REQUEST);
                    alerta.addReceiver(policia);
                    alerta.setContent("INTERVENCION_ASALTO");
                    send(alerta);

                    printAlert("Policía solicitada");
                } else {
                    printAlert("No se encontró policía");
                }
            }

            else if (msg.getContent().equals("LADRON_ARRESTADO")) {
                printAnimated("Policía confirmó arresto");

                ACLMessage avisoCajero = new ACLMessage(ACLMessage.INFORM);
                avisoCajero.setContent("CAJA_DESBLOQUEADA");
                avisoCajero.addReceiver(buscarAgente("servicio-caja"));

                send(avisoCajero);

                printAnimated("Caja habilitada nuevamente");
            }
        }
    }

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
        printAnimated("Pedido enviado a cocina");
    }

    private void enviarACaja(AID cajero, String idPlatillo) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(cajero);
        msg.setContent("COBRAR_ID=" + idPlatillo);
        send(msg);
        printAnimated("Pedido enviado a caja");
    }

    private void reenviarBoletaAlCliente(String boleta) {
        if (clienteActual != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(clienteActual);
            msg.setContent(boleta);
            msg.setConversationId("recibir-boleta"); 
            send(msg);
            printAnimated("Boleta enviada al cliente");
        }
    }

    private void informarCliente(String mensaje) {
        if (clienteActual != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(clienteActual);
            msg.setContent(mensaje);
            msg.setConversationId("pedido-comida");
            send(msg);
        }
    }
}
