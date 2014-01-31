package net.md_5.bungee.module;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.util.CaseInsensitiveMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class ModuleManager
{

    private final Map<String, ModuleSource> knownSources = new HashMap<>();

    public ModuleManager()
    {
        knownSources.put( "jenkins", new JenkinsModuleSource() );
    }

    public void load(ProxyServer proxy, File moduleDirectory) throws Exception
    {
        ModuleVersion bungeeVersion = ModuleVersion.parse( proxy.getVersion() );
        if ( bungeeVersion == null )
        {
            System.out.println( "Could detect bungee version. Custom build?" );
            return;
        }

        moduleDirectory.mkdir();

        List<ModuleSpec> modules = new ArrayList<>();
        File configFile = new File( "modules.yml" );
        // Start Yaml
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle( DumperOptions.FlowStyle.BLOCK );
        Yaml yaml = new Yaml( options );

        Map<String, Object> config;

        configFile.createNewFile();
        try ( InputStream is = new FileInputStream( configFile ) )
        {
            config = (Map) yaml.load( is );
        }

        if ( config == null )
        {
            config = new CaseInsensitiveMap();
        } else
        {
            config = new CaseInsensitiveMap( config );
        }
        // End yaml

        List<String> defaults = new ArrayList<>();
        if ( config.containsKey( "modules" ) )
        {
            defaults.addAll( (Collection) config.get( "modules" ) );
        }
        int version = ( config.containsKey( "version" ) ) ? (int) config.get( "version" ) : 0;
        switch ( version )
        {
            case 0:
                defaults.add( "jenkins://cmd_alert" );
                defaults.add( "jenkins://cmd_find" );
                defaults.add( "jenkins://cmd_list" );
                defaults.add( "jenkins://cmd_send" );
                defaults.add( "jenkins://cmd_server" );
            case 1:
                defaults.add( "jenkins://reconnect_yaml" );
        }
        config.put( "modules", defaults );
        config.put( "version", 2 );

        try ( FileWriter wr = new FileWriter( configFile ) )
        {
            yaml.dump( config, wr );
        }

        for ( String s : (List<String>) config.get( "modules" ) )
        {
            URI uri = new URI( s );

            ModuleSource source = knownSources.get( uri.getScheme() );
            if ( source == null )
            {
                System.out.println( "Unknown module source: " + s );
                continue;
            }
            String name = uri.getAuthority();
            if ( name == null )
            {
                System.out.println( "Unknown module host: " + s );
                continue;
            }

            ModuleSpec spec = new ModuleSpec( name, new File( moduleDirectory, name + ".jar" ), source );
            modules.add( spec );
            System.out.println( "Discovered module: " + spec );
        }

        for ( ModuleSpec module : modules )
        {
            ModuleVersion moduleVersion = ( module.getFile().exists() ) ? getVersion( module.getFile() ) : null;

            if ( !bungeeVersion.equals( moduleVersion ) )
            {
                System.out.println( "Attempting to update plugin from " + moduleVersion + " to " + bungeeVersion );
                module.getProvider().retrieve( module, bungeeVersion );
            }
        }
    }

    private ModuleVersion getVersion(File file)
    {
        try ( JarFile jar = new JarFile( file ) )
        {
            JarEntry pdf = jar.getJarEntry( "plugin.yml" );
            Preconditions.checkNotNull( pdf, "Plugin must have a plugin.yml" );

            try ( InputStream in = jar.getInputStream( pdf ) )
            {
                PluginDescription desc = new Yaml().loadAs( in, PluginDescription.class );
                return ModuleVersion.parse( desc.getVersion() );
            }
        } catch ( Exception ex )
        {
            ProxyServer.getInstance().getLogger().log( Level.WARNING, "Could not check module from file " + file, ex );
        }

        return null;
    }
}