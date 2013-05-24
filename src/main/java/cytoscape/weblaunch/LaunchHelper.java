package cytoscape.weblaunch;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Properties;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

public class LaunchHelper {

	private static String[] versions = new String[] {"Cytoscape_v3.0.1","Cytoscape_v3.0.0"};

	private static String[] pluginUrls = new String[] { 
			"http://chianti.ucsd.edu/~thully/plugins/genomespace-cytoscape-2.0-SNAPSHOT.jar" // GenomeSpace
		};

	private static String installerURL = "http://www.cytoscape.org/download.html";

	private static final String MAC = "mac os x";
	private static final String WINDOWS = "windows";
	private static final String PREFERRED_PATH = "preferred.path";

	public static void main(String[] args) {
		String os = System.getProperty("os.name").toLowerCase();

		String exe = getExecutable(os);
		String path = getBestGuessPath(os,exe);
		path = validatePath(path, exe); 

		if ( path == null )
			return;

		// OK, all systems go!

		downloadPlugins();
		String[] command = createCommand(getFile(path,exe),args);

		storePreferredPath(path);

		launch(command);
	}

	private static void launch(final String[] command) {
		try {
			Runtime rt = Runtime.getRuntime();
			Process p = rt.exec(command);
			StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");
			StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
			errorGobbler.start();
			outputGobbler.start();
			int exitVal = p.waitFor();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
							  "Error launching: " + command[0] + "\n\nCaused by\n" + e.getMessage(),
							  "Could Not Launch Cytoscape",
							  JOptionPane.ERROR_MESSAGE);
		}
	}

	private static void storePreferredPath(String path) {
		try {
			Properties props = new Properties();
			props.setProperty(PREFERRED_PATH,path);
			props.store( new FileOutputStream( getPropsFile() ), "Properties for GenomeSpace");
		} catch (IOException ioe) { ioe.printStackTrace(System.err); }
	}

	private static String validatePath(final String inpath, final String exe) {
		String path = inpath;

		if ( !executableExists(path,exe) ) {
			File file = getUserSelectedDirectory();
			if ( file != null )
				path = file.getAbsolutePath();
		}

		while ( !executableExists(path,exe) ) {
			int res = JOptionPane.showConfirmDialog(null, "We can't find the Cytoscape executable in the specified location.\nWould you like to try another location?", "Select Cytoscape Installation Directory", JOptionPane.YES_NO_OPTION);	

			if ( res == JOptionPane.YES_OPTION ) {
				JFileChooser fc = new JFileChooser();
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				fc.setDialogTitle("Select Cytoscape Installation Directory");
 
				int returnVal = fc.showOpenDialog(null);
				if (returnVal == JFileChooser.APPROVE_OPTION) 
					path = fc.getSelectedFile().getAbsolutePath();
			} else {
				return null;
			}
		}

		return path;
	}

	private static String[] createCommand(final File f, final String[] args) {
		String[] command = new String[ args.length + 1];
		int i = 0;
		command[i] = f.getAbsolutePath();
		for( String arg : args) {
			command[++i] = arg;
		}
		return command;
	}

	private static void downloadPlugins() {
		for (String url : pluginUrls) { 
			File f = downloadURL(url);
			if(f == null)
				System.err.println("Couldn't download plugin URL: " + url);
		}
	}

	private static File downloadURL(final String u) {
		File f = null;
		FileOutputStream fos = null; 
		try {
			URL url = new URL(u);
			String name = url.getPath().substring(url.getPath().lastIndexOf("/"));
			String home = System.getProperty("user.home");
			String sep = System.getProperty("file.separator");
			f = new File(home + sep + "CytoscapeConfiguration" + sep +
					"3" + sep + "apps" + sep + "installed" + sep + name );
			ReadableByteChannel rbc = Channels.newChannel(url.openStream());
			fos = new FileOutputStream(f);
			fos.getChannel().transferFrom(rbc, 0, 1 << 24);
			fos.close();
		} catch (Exception e) {
			e.printStackTrace(System.err);
		} finally {
			if ( fos != null ) {
				try { fos.close(); } catch (IOException ioe) { ioe.printStackTrace(System.err); }
				fos = null;
			}
		}
		return f;
	}

	private static boolean executableExists(final String path, final String exe) {
		if ( path == null || exe == null )
			return false;
		File file = getFile(path,exe); 
		return file.exists(); 
	}

	private static File getFile(final String path, final String exe) {
		return new File(path + System.getProperty("file.separator") + exe);
	}

	private static String getExecutable(final String os) {
		String exe; 
		if (os.startsWith(WINDOWS))	
			exe = "cytoscape.bat"; 
		else 
			exe = "cytoscape.sh";

		return exe;
	}

	private static String getBestGuessPath(final String os, final String exe) {
		String path = getPreferredPath();
		if ( path != null )
			return path;
	
		if (os.equals(MAC))  
			path = "/Applications";
		else						
			path = System.getProperty("user.home");

		// allow older versions of cytoscape 
		for ( String version : versions ) {	
			String npath = path + "/" + version;
			if ( executableExists(npath,exe) ) {
				path = npath;
				break;	
			} 
		}
				
		return path;
	}

	private static File getPropsFile() {
		File f = new File( System.getProperty("user.home") + System.getProperty("file.separator") + 
		                   "CytoscapeConfiguration" +  System.getProperty("file.separator") + "genomespace-cytoscape.props" );
		return f;
	}

	private static String getPreferredPath() {
		try { 
			File f = getPropsFile(); 
			if ( !f.exists() )
				return null;

			Properties props = new Properties();
			props.load( new FileInputStream(f) );
			return props.getProperty(PREFERRED_PATH);

		} catch (IOException ioe) { ioe.printStackTrace(System.err); }
		return null;
	}

	private static void openURL(final String urlString) {
		URL url = null; 

		try {

			url = new URL(urlString);

			if (!Desktop.isDesktopSupported()) {
				JOptionPane.showMessageDialog(null,
								  "Java Desktop is not supported",
								  "Cannot Launch Link",
								  JOptionPane.WARNING_MESSAGE);
				return;
			}

			Desktop desktop = Desktop.getDesktop();
			if (!desktop.isSupported(Desktop.Action.BROWSE)) {
				JOptionPane.showMessageDialog(null,
								  "Java Desktop doesn't support the browse action",
								  "Cannot Launch Link",
								  JOptionPane.WARNING_MESSAGE);
				return;
			}

			if (url == null) {
				JOptionPane.showMessageDialog(null,
								  "InvalidURL: Failed to launch the link to installer:\n"+installerURL,
								  "Cannot Launch Link",
								  JOptionPane.ERROR_MESSAGE);
				return;
			}
	
			URI uri = new URI(url.toString());
			desktop.browse(uri);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
							  "Exception: Failed to launch the link to installer:\n"+url,
							  "Could Not Open Link",
							  JOptionPane.ERROR_MESSAGE);
		}
	}

	private static File getUserSelectedDirectory() {
		int res = JOptionPane.showConfirmDialog(null, "Is Cytoscape installed on this computer?", "Select Cytoscape Installation Directory", JOptionPane.YES_NO_OPTION);	

		if ( res == JOptionPane.YES_OPTION ) {
			JOptionPane.showMessageDialog(null, "Please choose the directory where\nCytoscape is installed on your system.", "Select Cytoscape Installation Directory", JOptionPane.INFORMATION_MESSAGE);	
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fc.setDialogTitle("Select Cytoscape Installation Directory");
 
			int returnVal = fc.showOpenDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) 
				return fc.getSelectedFile();
			else
				return null;
		} else {
			JOptionPane.showMessageDialog(null, "Please download and install Cytoscape.\nWe'll open a browser to the download page.", "Select Cytoscape Installation Directory", JOptionPane.INFORMATION_MESSAGE);	
			openURL(installerURL);
			return null;
		}
	}
}
