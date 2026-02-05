

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {

    public static void main(String[] args) {
        Runtime rt = Runtime.instance();

        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.GUI, "true"); // GUI JADE

        ContainerController container = rt.createMainContainer(profile);

        try {
            container.createNewAgent("Cliente", AgenteCliente.class.getName(), null).start();
            container.createNewAgent("Mesero", AgenteMesero.class.getName(), null).start();
            container.createNewAgent("Cocinero", AgenteCocinero.class.getName(), null).start();
            container.createNewAgent("Cajero", AgenteCajero.class.getName(), null).start();
            container.createNewAgent("Ladron", AgenteLadron.class.getName(), null).start();
            container.createNewAgent("Policia", AgentePolicia.class.getName(), null).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
