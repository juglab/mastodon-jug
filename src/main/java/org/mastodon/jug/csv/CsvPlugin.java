package org.mastodon.jug.csv;

import bdv.viewer.Source;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.UIManager;
import net.imglib2.realtransform.AffineTransform3D;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.project.MamutProject;
import org.mastodon.project.MamutProjectIO;
import org.mastodon.revised.mamut.KeyConfigContexts;
import org.mastodon.revised.mamut.MamutAppModel;
import org.mastodon.revised.mamut.Mastodon;
import org.mastodon.revised.model.mamut.Link;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelGraph;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.revised.ui.keymap.CommandDescriptionProvider;
import org.mastodon.revised.ui.keymap.CommandDescriptions;
import org.mastodon.revised.ui.util.FileChooser;
import org.scijava.AbstractContextual;
import org.scijava.Context;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.RunnableAction;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

@Plugin( type = MastodonPlugin.class )
public class CsvPlugin extends AbstractContextual implements MastodonPlugin
{
	private static final String IMPORT_CSV = "[juglab] import csv";
	private static final String EXPORT_CSV = "[juglab] export csv";

	private static final String[] IMPORT_CSV_KEYS = { "not mapped" };
	private static final String[] EXPORT_CSV_KEYS = { "not mapped" };

	private static Map< String, String > menuTexts = new HashMap<>();

	static
	{
		menuTexts.put( IMPORT_CSV, "Import CSV..." );
		menuTexts.put( EXPORT_CSV, "Export CSV..." );
	}

	/*
	 * Command descriptions for all provided commands
	 */
	@Plugin( type = Descriptions.class )
	public static class Descriptions extends CommandDescriptionProvider
	{
		public Descriptions()
		{
			super( KeyConfigContexts.TRACKSCHEME, KeyConfigContexts.BIGDATAVIEWER );
		}

		@Override
		public void getCommandDescriptions( final CommandDescriptions descriptions )
		{
			descriptions.add( IMPORT_CSV, IMPORT_CSV_KEYS, "Import CSV file (Anna's format)." );
			descriptions.add( EXPORT_CSV, EXPORT_CSV_KEYS, "Export CSV file (Anna's format)." );
		}
	}

	private final AbstractNamedAction importCsvAction;
	private final AbstractNamedAction exportCsvAction;

	private MastodonPluginAppModel pluginAppModel;

	public CsvPlugin()
	{
		importCsvAction = new RunnableAction( IMPORT_CSV, this::importCsv );
		exportCsvAction = new RunnableAction( EXPORT_CSV, this::exportCsv );
		updateEnabledActions();
	}

	@Override
	public void setAppModel( final MastodonPluginAppModel model )
	{
		this.pluginAppModel = model;
		updateEnabledActions();
	}

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		return Arrays.asList(
				menu( "Plugins",
						menu( "Jug lab",
								item( IMPORT_CSV ),
								item( EXPORT_CSV ) ) ) );
	}

	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}

	@Override
	public void installGlobalActions( final Actions actions )
	{
		actions.namedAction( importCsvAction, IMPORT_CSV_KEYS );
		actions.namedAction( exportCsvAction, EXPORT_CSV_KEYS );
	}

	private void updateEnabledActions()
	{
		final MamutAppModel appModel = ( pluginAppModel == null ) ? null : pluginAppModel.getAppModel();
		importCsvAction.setEnabled( appModel != null );
		exportCsvAction.setEnabled( appModel != null );
	}

	private String importCsvPath = null;

	private void importCsv()
	{
		final File file = FileChooser.chooseFile(
				null,
				importCsvPath,
				null,
				"Open CSV File",
				FileChooser.DialogType.LOAD );
		if ( file == null )
			return;
		else
		{
			try
			{
				importCsvPath = file.getCanonicalPath();
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}

		final double radius = 3;

		final CSVFormat csvFormat = CSVFormat.DEFAULT
				.withDelimiter( '\t' )
				.withHeader();

		final Model model = pluginAppModel.getAppModel().getModel();
		final Source< ? > source = pluginAppModel.getAppModel().getSharedBdvData().getSources().get( 0 ).getSpimSource();

		final ModelGraph graph = model.getGraph();
		final Spot vref = graph.vertexRef();

		final ReentrantReadWriteLock.WriteLock lock = graph.getLock().writeLock();
		lock.lock();
		try ( CSVParser records = csvFormat.parse( new FileReader( file ) ) )
		{
			AffineTransform3D tf = new AffineTransform3D();
			final double[] pos = new double[ 3 ];
			for ( CSVRecord record : records )
			{
				final double x = Double.parseDouble( record.get( "Center X" ) );
				final double y = Double.parseDouble( record.get( "Center Y" ) );
				final double z = Double.parseDouble( record.get( "Center Z" ) );
				final int frame = Integer.parseInt( record.get( "Frame" ) );
				final String name = record.get( "Name" );

				source.getSourceTransform( frame, 0, tf );
				tf.apply( new double[] { x, y, z }, pos );
				graph.addVertex( vref ).init( frame, pos, radius ).setLabel( name );
			}
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		finally
		{
			lock.unlock();
			graph.releaseRef( vref );
		}
	}

	private String exportCsvPath = null;

	private void exportCsv()
	{
		final File file = FileChooser.chooseFile(
				null,
				exportCsvPath,
				null,
				"Save CSV File",
				FileChooser.DialogType.SAVE );
		if ( file == null )
			return;
		else
		{
			try
			{
				exportCsvPath = file.getCanonicalPath();
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}

		final CSVFormat csvFormat = CSVFormat.DEFAULT
				.withDelimiter( '\t' )
				.withHeader( "from", "to", "from_frame", "to_frame" );

		final Model model = pluginAppModel.getAppModel().getModel();
		final ModelGraph graph = model.getGraph();
		final Spot vref1 = graph.vertexRef();
		final Spot vref2 = graph.vertexRef();

		final ReentrantReadWriteLock.ReadLock lock = graph.getLock().readLock();
		lock.lock();
		try ( CSVPrinter printer = new CSVPrinter( new FileWriter( file ), csvFormat ) )
		{
			for ( Link edge : graph.edges() )
			{
				final Spot source = edge.getSource( vref1 );
				final Spot target = edge.getTarget( vref2 );
				printer.printRecord(
						source.getLabel(),
						target.getLabel(),
						source.getTimepoint(),
						target.getTimepoint() );
			}
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		finally
		{
			lock.unlock();
			graph.releaseRef( vref1 );
			graph.releaseRef( vref2 );
		}
	}

	/*
	 * Start Mastodon (for testing)...
	 */

	public static void main( final String[] args ) throws Exception
	{
		final String projectPath = "/Users/pietzsch/Desktop/anna/Organoid_tracking/Pos14_1um_7min_46h_with_tracks.mastodon";

		Locale.setDefault( Locale.US );
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		final Mastodon mastodon = new Mastodon();
		new Context().inject( mastodon );
		mastodon.run();

		final MamutProject project = new MamutProjectIO().load( projectPath );
		mastodon.openProject( project );
	}
}
