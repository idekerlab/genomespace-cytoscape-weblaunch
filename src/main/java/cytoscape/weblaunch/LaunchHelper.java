package cytoscape.weblaunch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Properties;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

public class LaunchHelper {

	private static String[] versions = new String[] {"Cytoscape_v3.0.1","Cytoscape_v3.0.0"};
	private static String[] appUrls = new String[] { 
			"http://chianti.ucsd.edu/~thully/plugins/genomespace-cytoscape-2.0-SNAPSHOT.jar" // GenomeSpace
		};

	private static String win64InstallerURL = "http://chianti.ucsd.edu/cytoscape-3.0.1/Cytoscape_3_0_1_windows_64bit.exe";
	private static String win32InstallerURL = "http://chianti.ucsd.edu/cytoscape-3.0.1/Cytoscape_3_0_1_windows_32bit.exe";
	private static String unixInstallerURL = "http://chianti.ucsd.edu/cytoscape-3.0.1/Cytoscape_3_0_1_unix.sh";
	private static String macInstallerURL = "http://chianti.ucsd.edu/cytoscape-3.0.1/Cytoscape_3_0_1_macos.dmg";

	private static final String MAC = "mac os x";
	private static final String WINDOWS = "windows";
	private static final String PREFERRED_PATH = "preferred.path";

	public static void main(String[] args) {
		String os = System.getProperty("os.name").toLowerCase();
		String arch = System.getProperty("os.arch");
		String exe = getExecutable(os);
		String bestGuessPath = getBestGuessPath(os,exe);
		String path = checkForCytoscapeInstallation(bestGuessPath, os, arch, exe); 

		if ( path == null )
			return;

		// OK, all systems go!
		File file = getFile(path,exe);
		downloadApps();
		String[] command = createCommand(file,args,os);

		storePreferredPath(path);

		launch(command, file.getParentFile());
	}

	private static Process launch(final String[] command, final File path) {
		try {
			Runtime rt = Runtime.getRuntime();
			Process p = rt.exec(command, null, path);
			StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");
			StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
			errorGobbler.start();
			outputGobbler.start();
			return p;
		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
							  "Error launching: " + command[0] + "\n\nCaused by\n" + e.getMessage(),
							  "Error",
							  JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}

	private static void storePreferredPath(String path) {
		try {
			Properties props = new Properties();
			props.setProperty(PREFERRED_PATH,path);
			props.store( new FileOutputStream( getPropsFile() ), "Properties for GenomeSpace");
		} catch (IOException ioe) { ioe.printStackTrace(System.err); }
	}

	private static String checkForCytoscapeInstallation(final String inpath, final String os, final String arch, final String exe) {
		String path = inpath;

		if ( !executableExists(path,exe) ) {
			int res = JOptionPane.showConfirmDialog(null, "Is Cytoscape installed on this computer?", "Select Cytoscape Installation Directory", JOptionPane.YES_NO_OPTION);	

			if(res == JOptionPane.NO_OPTION) {
				res = JOptionPane.showConfirmDialog(null, "Do you want to install Cytoscape on this computer?", "Install Cytoscape", JOptionPane.YES_NO_OPTION);	
				if(res == JOptionPane.YES_OPTION) {
					installCytoscape(os, arch);
				}
				else
					return null;
			}
			
			if ( !executableExists(path,exe) ) {
				JOptionPane.showMessageDialog(null, "Please choose the directory where\nCytoscape is installed on your system.", "Select Cytoscape Installation Directory", JOptionPane.INFORMATION_MESSAGE);	
				File file = selectCytoscapeInstallationDirectory();
				if ( file != null )
					path = file.getAbsolutePath();
			} 
		}

		while ( !executableExists(path,exe) ) {
			int res = JOptionPane.showConfirmDialog(null, "We can't find the Cytoscape executable in the specified location.\nWould you like to try another location?", "Select Cytoscape Installation Directory", JOptionPane.YES_NO_OPTION);	

			if ( res == JOptionPane.YES_OPTION ) {
				File file = selectCytoscapeInstallationDirectory();
				if ( file != null )
					path = file.getAbsolutePath();
			} else {
				return null;
			}
		}

		return path;
	}

	private static String[] createCommand(final File f, final String[] args, final String os) {
		int i = -1;
		String[] command;
		if(os.startsWith(WINDOWS)) {
			command = new String[ args.length + 3];
			command[++i] = "cmd.exe";
			command[++i] = "/c";
		}
		else {
			command = new String[ args.length + 1];
		}
		command[++i] = f.getAbsolutePath();
		for( String arg : args) {
			command[++i] = arg;
		}
		return command;
	}

	private static void downloadApps() {
		for (String url : appUrls) { 
			String home = System.getProperty("user.home");
			String path = home + File.separator + "CytoscapeConfiguration" + File.separator +
					"3" + File.separator + "apps" + File.separator + "installed" + File.separator;
			File f = downloadURL(url, path);
			if(f == null)
				System.err.println("Couldn't download plugin URL: " + url);
		}
	}
	
	private static File downloadURL(final String u) {
		return downloadURL(u, null);
	}
	
	private static File downloadURL(final String u, final String path) {
		File f = null;
		FileOutputStream fos = null; 
		try {
			URL url = new URL(u);
			String name = url.getPath().substring(url.getPath().lastIndexOf("/")+1);
			if(path == null){ 
				int dotIndex = name.lastIndexOf(".");
				String prefix = name.substring(0, dotIndex);
				String suffix = name.substring(dotIndex);
				f=File.createTempFile(prefix,suffix);
			}
			else{
				f=new File(path);
				if(f.isDirectory()) {
					f = new File(f, name);
				}
			}
			ReadableByteChannel rbc = Channels.newChannel(url.openStream());
			fos = new FileOutputStream(f);
			fos.getChannel().transferFrom(rbc, 0, 1 << 30);
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
		return new File(path + File.separator + exe);
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
		if(os.startsWith(WINDOWS))
			path = "C:" + File.separator + "Program Files";
		else if(os.startsWith(MAC))  
			path = File.separator + "Applications";
		else						
			path = System.getProperty("user.home");

		// allow older versions of cytoscape 
		for ( String version : versions ) {	
			String npath = path + File.separator + version;
			if ( executableExists(npath,exe) ) {
				path = npath;
				break;	
			} 
		}
				
		return path;
	}

	private static File getPropsFile() {
		File f = new File( System.getProperty("user.home") + File.separator + 
		                   "CytoscapeConfiguration" +  File.separator + "genomespace-cytoscape.props" );
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

	

	private static File selectCytoscapeInstallationDirectory() {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setDialogTitle("Select Cytoscape Installation Directory");

		int returnVal = fc.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) 
			return fc.getSelectedFile();
		else
			return null;
	}
	
	private static void installCytoscape(final String os, final String arch) {
		String installerURL = "";
		if(os.startsWith(WINDOWS)) {
			if(arch.contains("64")) 
				installerURL = win64InstallerURL;
			else
				installerURL = win32InstallerURL;
		}
		else
			installerURL = unixInstallerURL;
		File installer = downloadURL(installerURL);
		installer.setExecutable(true);
		String[] command = createCommand(installer, new String[0], os);
		try {
			launch(command, installer.getParentFile()).waitFor();
		}
		catch(InterruptedException e){}
	}
}
