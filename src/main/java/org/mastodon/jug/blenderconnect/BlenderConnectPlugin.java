package org.mastodon.jug.blenderconnect;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.graph.GraphIdBimap;
import org.mastodon.ipc.VertexPy4JDTO;
import org.mastodon.model.SelectionModel;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutMenuBuilder;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.ui.keymap.CommandDescriptionProvider;
import org.mastodon.revised.ui.keymap.CommandDescriptions;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import py4j.GatewayServer;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Plugin(type = MastodonPlugin.class)
public class BlenderConnectPlugin implements MastodonPlugin {
    private static final String ACTION_1 = "[[juglab]] Connect to Blender";
    private static final String ACTION_2 = "[[juglab]] Disconnect from Blender";

    private static final String[] ACTION_1_KEYS = new String[]{"not mapped" };
    private static final String[] ACTION_2_KEYS = new String[]{"not mapped" };
    private static Map<String, String> menuTexts = new HashMap<>();

    static {
        menuTexts.put(ACTION_1, "Connect to Blender");
        menuTexts.put(ACTION_2, "Disconnect from Blender");
    }

    private final AbstractNamedAction action1 = new AbstractNamedAction(ACTION_1) {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(final ActionEvent e) {
            server = new GatewayServer();
            server.start();
            System.out.println("Gateway Server for py4j started.");
            Runtime.getRuntime().addShutdownHook(new Thread()
            {
                public void run()
                {
                    server.shutdown();
                    System.out.println("Gateway Server for py4j stopped.");
                }
            });
        }
    };

    private final AbstractNamedAction action2 = new AbstractNamedAction(ACTION_2) {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(final ActionEvent e) {
            server.shutdown();
            System.out.println("Gateway Server for py4j stopped.");
        }
    };
    @SuppressWarnings("unused")
    private MastodonPluginAppModel appModel;
    private GatewayServer server;

    @Override
    public List<ViewMenuBuilder.MenuItem> getMenuItems() {
        return Arrays.asList(
                MamutMenuBuilder.menu("Plugins",
                        MamutMenuBuilder.menu("Jug lab",
                                MamutMenuBuilder.item(ACTION_1), MamutMenuBuilder.item(ACTION_2))));
    }

    @Override
    public Map<String, String> getMenuTexts() {
        return menuTexts;
    }

    @Override
    public void installGlobalActions(final Actions actions) {
        actions.namedAction(action1, ACTION_1_KEYS);
        actions.namedAction(action2, ACTION_2_KEYS);
    }

    @Override
    public void setAppModel(final MastodonPluginAppModel appModel) {

        this.appModel = appModel;
        GatewayServer.turnLoggingOff();



        final SelectionModel<Spot, Link> selectionModel = appModel.getAppModel().getSelectionModel();
        selectionModel.listeners().add(() -> listenForSelection(appModel));
    }

    private void listenForSelection(MastodonPluginAppModel appModel) {
        final SelectionModel<Spot, Link> selectionModel = appModel.getAppModel().getSelectionModel();

        if(server != null){
            VertexPy4JDTO transfer = (VertexPy4JDTO) server.getPythonServerEntryPoint(new Class[] { VertexPy4JDTO.class });

            final GraphIdBimap<Spot, Link> graphIdBimap = appModel.getAppModel().getModel().getGraphIdBimap();
            selectionModel.getSelectedVertices().forEach(selectedVertex -> {
                final int id = graphIdBimap.getVertexId(selectedVertex);
                final String label = selectedVertex.getLabel();
                System.out.println("Selected vertex:" + id + " : " + label);

                try {
                    transfer.sendId(id);
                    transfer.sendIdLabel(id, label);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.out.println("Please reconnect the Py4J Gateway Server through the Plugin Menu");
                }

            });
        }

    }

    /*
     * Command descriptions for all provided commands
     */
    @Plugin(type = CommandDescriptionProvider.class)
    public static class Descriptions extends CommandDescriptionProvider {
        public Descriptions() {
            super(KeyConfigContexts.MASTODON);
        }

        @Override
        public void getCommandDescriptions(final CommandDescriptions descriptions) {
            descriptions.add(ACTION_1, ACTION_1_KEYS, "Starts the Py4J Gateway Server to connect to Blender");
            descriptions.add(ACTION_2, ACTION_2_KEYS, "Stops the Py4J Gateway Server.");
        }
    }

}
