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
    private static final String ACTION_1 = "[BlenderConnectPlugin] action1";

    private static final String[] ACTION_1_KEYS = new String[]{"meta K"};

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
            descriptions.add(ACTION_1, ACTION_1_KEYS, "Blender Connect Plugin Action");
        }
    }

    @SuppressWarnings("unused")
    private MastodonPluginAppModel appModel;

    private static Map<String, String> menuTexts = new HashMap<>();

    static {
        menuTexts.put(ACTION_1, "Test Action");
    }

    private final AbstractNamedAction action1 = new AbstractNamedAction(ACTION_1) {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(final ActionEvent e) {
            System.out.println("BlenderConnectPlugin.action1.actionPerformed");
        }
    };

    @Override
    public List<ViewMenuBuilder.MenuItem> getMenuItems() {
        return Arrays.asList(
                MamutMenuBuilder.menu("Plugins",
                        MamutMenuBuilder.item(ACTION_1)));
    }

    @Override
    public Map<String, String> getMenuTexts() {
        return menuTexts;
    }

    @Override
    public void installGlobalActions(final Actions actions) {
        actions.namedAction(action1, ACTION_1_KEYS);
    }

    @Override
    public void setAppModel(final MastodonPluginAppModel appModel) {

        this.appModel = appModel;
        GatewayServer.turnLoggingOff();
        GatewayServer server = new GatewayServer();
        server.start();
        final SelectionModel<Spot, Link> selectionModel = appModel.getAppModel().getSelectionModel();
        selectionModel.listeners().add(() -> listenForSelection(appModel, server));
        final Spot someSpot = appModel.getAppModel().getModel().getSpatioTemporalIndex().iterator().next();
        selectionModel.setSelected(someSpot, true);
    }

    private void listenForSelection(MastodonPluginAppModel appModel, GatewayServer server) {
        appModel.getAppModel().getModel().getGraph().vertices().forEach(spot -> {
            final SelectionModel<Spot, Link> selectionModel = appModel.getAppModel().getSelectionModel();

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
                    e.printStackTrace();
                }

            });
        });
    }

}

