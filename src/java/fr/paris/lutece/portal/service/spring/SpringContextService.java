/*
 * Copyright (c) 2002-2023, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.portal.service.spring;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

import fr.paris.lutece.portal.service.init.LuteceInitException;
import fr.paris.lutece.portal.service.init.WebConfResourceLocator;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginEvent;
import fr.paris.lutece.portal.service.plugin.PluginEventListener;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.plugin.LegacyPluginEventObserver;
import fr.paris.lutece.portal.service.util.AppLogService;

/**
 * This class provides a way to use Spring Framework ligthweight containers offering IoC (Inversion of Control) features.
 * 
 * @see <a href= "http://www.springframework.org">http://www.springframework.org</a>
 */
public final class SpringContextService implements PluginEventListener
{
    private static final String PROTOCOL_FILE = "file:";
    private static final String PATH_CONF = "/WEB-INF/conf/";
    private static final String SUFFIX_CONTEXT_FILE = "_context.xml";
    private static final String FILE_CORE_CONTEXT = "core_context.xml";
    private static ApplicationContext _context;
    private static ApplicationContext _parentcontext;

    private static Map<Class, List> _mapBeansOfType = new HashMap<>( );
    private static SpringContextService _instance = new SpringContextService( );

    /** Creates a new instance of SpringContextService */
    private SpringContextService( )
    {
    }

    /**
     * Return an instance, which may be shared or independent, of the given bean name. This method allows a Spring BeanFactory to be used as a replacement for
     * the Singleton or Prototype design pattern.<br>
     * The bean is retreived from the main context defined in the WEB-INF/conf/core_context.xml.
     *
     * @param <T>
     *            the generic type
     * @param strName
     *            The bean's name
     * @return The instance of the bean
     */
    public static <T> T getBean( String strName )
    {
        return (T) _context.getBean( strName );
    }

    public static <T> T getBean( String strName, Class<T> t )
    {
        T tt = (T) _context.getBean( strName, t );
        return tt;
    }

    /**
     * Return an instance of the given bean name loaded by the a Spring BeanFactory. The bean is retreived from a plugin context defined in the
     * WEB-INF/conf/plugins/[plugin_name]_context.xml.
     * 
     * @param strPluginName
     *            The Plugin's name
     * @param strName
     *            The bean's name
     * @return The instance of the bean
     * @deprecated use {@link #getBean(String)} instead
     */
    @Deprecated
    public static Object getPluginBean( String strPluginName, String strName )
    {
        return _context.getBean( strName );
    }

    /**
     * Indicates if a bean, referenced by its name, is part of an enabled plugin.
     * 
     * Per Lutece convention, the plugin is determined from the bean's name prefix. If no prefix is present, the bean is considered enabled.
     * 
     * @param strBeanName
     *            The bean's name
     * @return <code>true</code> if the bean is part of an enabled plugin, <code>false</code> otherwise
     */
    public static boolean isBeanEnabled( String strBeanName )
    {
        String strPrefix = getPrefix( strBeanName );
        return strPrefix == null || isEnabled( strPrefix );
    }

    /**
     * Initialize a global Application Context containing all beans (core + plugins) Now uses GenericApplicationContext for better performances. A wrong
     * formatted file will not block block context to be built (without the file), but a wrong bean (i.e. cannot be instantiated) will cause a full context
     * failure. Context is less "failure-friendly"
     * 
     * @param servletContext
     *            The servlet context
     * @throws LuteceInitException
     *             The lutece init exception
     * @since 2.4
     */
    public static void init( ServletContext servletContext ) throws LuteceInitException
    {
        try
        {
            // Register this service as a PluginEventListener
            LegacyPluginEventObserver.registerPluginEventListener( _instance );
            GenericWebApplicationContext gwac = new GenericWebApplicationContext( servletContext );
            gwac.setParent( _parentcontext );
            gwac.setId( getContextName( servletContext ) );
            gwac.refresh( );
            _context = gwac;

        }
        catch( Exception e )
        {
            AppLogService.error( "Error initializing Spring Context Service {}", e.getMessage( ), e );
            throw new LuteceInitException( "Error initializing Spring Context Service", e );
        }
    }

    /**
     * Returns a name for this context
     * 
     * @param servletContext
     *            the servlet context
     * @return name for this context
     */
    private static String getContextName( ServletContext servletContext )
    {
        String name = "lutece";

        if ( servletContext != null )
        {
            String contextName = servletContext.getServletContextName( );

            if ( contextName == null )
            {
                contextName = servletContext.getContextPath( );
            }

            if ( StringUtils.isNotBlank( contextName ) )
            {
                name = contextName;
            }
        }

        return name;
    }

    /**
     * Loads plugins contexts.
     * 
     * @param filesContext
     *            context files names
     * @param strConfPluginsPath
     *            full path
     * @param xmlReader
     *            the xml reader
     */
    public static void loadContexts( String [ ] filesContext, String strConfPluginsPath, XmlBeanDefinitionReader xmlReader )
    {
        if ( filesContext != null )
        {
            for ( String fileContext : filesContext )
            {
                String [ ] file = {
                        PROTOCOL_FILE + strConfPluginsPath + fileContext
                };

                // Safe loading of plugin context file
                try
                {
                    xmlReader.loadBeanDefinitions( file );
                    AppLogService.info( "Context file loaded : {}", fileContext );
                }
                catch( Exception e )
                {
                    AppLogService.error( "Unable to load Spring context file : {} - cause :  {}", fileContext, e.getMessage( ), e );
                }
            }
        }
    }
    
    /**
     * Loads plugins contexts.
     * 
     * @param filesContext
     *            context files paths
     * @param xmlReader
     *            the xml reader
     */
    public static void loadContexts( Set<String> filesContext, XmlBeanDefinitionReader xmlReader )
    {
        if ( filesContext != null )
        {
            for ( String fileContext : filesContext )
            {
                String [ ] file = {
                        PROTOCOL_FILE + fileContext
                };

                // Safe loading of plugin context file
                try
                {
                    xmlReader.loadBeanDefinitions( file );
                    AppLogService.info( "Context file loaded : {}", fileContext );
                }
                catch( Exception e )
                {
                    AppLogService.error( "Unable to load Spring context file : {} - cause :  {}", fileContext, e.getMessage( ), e );
                }
            }
        }
    }

    /**
     * Gets the application context
     *
     * @return The application context
     */
    public static ApplicationContext getContext( )
    {
        return _context;
    }

    /**
     * Gets the application context
     *
     * @return The application context
     */
    public static ApplicationContext getParentContext( )
    {
        return _parentcontext;
    }

    /**
     * Returns a list of bean among all that implements a given interface or extends a given class
     * 
     * @param <T>
     *            The class type
     * @param classDef
     *            The class type
     * @return A list of beans
     */
    public static <T> List<T> getBeansOfType( Class<T> classDef )
    {
        // Search the list in the cache
        List<T> list = _mapBeansOfType.get( classDef );

        if ( list != null )
        {
            return new ArrayList<>( list );
        }

        // The list is not in the cache, so we have to build it
        list = new ArrayList<>( );

        Map<String, T> map = BeanFactoryUtils.beansOfTypeIncludingAncestors(_context, classDef );
        String [ ] sBeanNames = map.keySet( ).toArray( new String [ map.size( )] );

        for ( String strBeanName : sBeanNames )
        {
            String strPluginPrefix = getPrefix( strBeanName );

            if ( ( strPluginPrefix == null ) || ( isEnabled( strPluginPrefix ) ) )
            {
                list.add( map.get( strBeanName ) );
            }
        }

        _mapBeansOfType.put( classDef, new ArrayList<>( list ) );

        return list;
    }

    /**
     * Gets the prefix of the bean (supposed to be the plugin name)
     * 
     * @param strBeanName
     *            The bean name
     * @return The prefix
     */
    private static String getPrefix( String strBeanName )
    {
        int nPos = strBeanName.indexOf( '.' );

        if ( nPos > 0 )
        {
            return strBeanName.substring( 0, nPos );
        }

        return null;
    }

    /**
     * Analyze a bean prefix to tell if it matchs an activated plugin
     * 
     * @param strPrefix
     *            The prefix of a bean
     * @return True if the prefix matchs an activated plugin
     */
    private static boolean isEnabled( String strPrefix )
    {
        Plugin plugin = PluginService.getPlugin( strPrefix );

        return ( plugin != null ) && plugin.isInstalled( );
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void processPluginEvent( PluginEvent event )
    {
        // Reset cache of beansOfType if a plugin is installed or uninstalled
        if ( ( event.getEventType( ) == PluginEvent.PLUGIN_INSTALLED || event.getEventType( ) == PluginEvent.PLUGIN_UNINSTALLED )
                && !_mapBeansOfType.isEmpty( ) )
        {
            _mapBeansOfType.clear( );
            AppLogService.info( "SpringService cache cleared due to a plugin installation change - Plugin : {}", event.getPlugin( ).getName( ) );
        }
    }

    /**
     * Closes the Spring context
     * 
     * @since 5.1.0
     */
    public static void shutdown( )
    {
        if ( _context != null )
        {
            ( (AbstractApplicationContext) _context ).close( );
        }
    }

    /**
     * Utils filename filter to identify context files
     */
    static class ContextFileFilter implements FilenameFilter
    {
        /**
         * Filter filename
         * 
         * @param file
         *            The current file
         * @param strName
         *            The file name
         * @return true if the file is a context file otherwise false
         */
        @Override
        public boolean accept( File file, String strName )
        {
            return strName.endsWith( SUFFIX_CONTEXT_FILE );
        }
    }

    /**
     * Initialization of a parent Spring context to be used in the CDI extension for registering Spring beans in the CDI container. We use this service to
     * maintain compatibility with plugins that depend on Spring.
     * 
     * @throws LuteceInitException
     */
    public static void initParentContext( ) throws LuteceInitException
    {
        try
        {
            // Load the core context file : core_context.xml
            String strContextFile = PROTOCOL_FILE + Thread.currentThread( ).getContextClassLoader( ).getResource( PATH_CONF + FILE_CORE_CONTEXT ).getPath( );

            GenericWebApplicationContext gwac = new GenericWebApplicationContext( );

            XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader( gwac );
            xmlReader.loadBeanDefinitions( strContextFile );
            AppLogService.info( "Context file loaded : {}", FILE_CORE_CONTEXT );

            // Load all context files found in the web conf resources
            // Files are loaded separately with an individual try/catch block
            // to avoid stopping the process in case of a failure
            // The global context generation will fail if a bean in any file cannot be
            // built.
            Set<String> xmlFilePaths = WebConfResourceLocator.getPathXmlFile( );
            Set<String> filesContext = new HashSet<String>( );
            for ( String xmlFilePath : xmlFilePaths )
            {
                if ( xmlFilePath.endsWith( SUFFIX_CONTEXT_FILE ) )
                {
                    filesContext.add( Thread.currentThread( ).getContextClassLoader( ).getResource( xmlFilePath ).getPath( ) );
                }
            }

            if ( !filesContext.isEmpty( ) )
            {
                SpringContextService.loadContexts( filesContext, xmlReader );
            }

            gwac.refresh( );
            _parentcontext = gwac;

        }
        catch( Exception e )
        {
            AppLogService.error( "Error initializing Root Spring Context Service {}", e.getMessage( ), e );
            throw new LuteceInitException( "Error initializing Spring Context Service", e );
        }
    }
}
