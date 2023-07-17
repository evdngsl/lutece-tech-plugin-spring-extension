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
package fr.paris.lutece.plugins.spring.extension.service;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import org.springframework.web.context.support.GenericWebApplicationContext;

import fr.paris.lutece.portal.service.init.AppInit;
import fr.paris.lutece.portal.service.init.LuteceInitException;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionTarget;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.servlet.ServletContext;

/**
 * This extension provides a consistent way to handle Spring beans alongside CDI beans. We use this extension to maintain compatibility with plugins that depend
 * on Spring.
 *
 */
public class SpringExtension implements Extension
{

    /**
     * init PropertiesServices before BeanDiscovery cdi event
     * 
     * @param bd
     *            the BeforeBeanDiscovery event
     */
    protected void initPropertiesServices( @Observes final BeforeBeanDiscovery bd )
    {
        AppInit.initPropertiesServices( "/WEB-INF/conf/", AppPathService.getWebAppPath( ) );

    }

    /**
     * Registration of beans instantiated by the Spring container in the CDI container.
     * 
     * @param abd
     *            AfterBeanDiscovery event
     * @param bm
     *            the BeanManager of cdi
     * @throws LuteceInitException
     *             the LuteceInitException
     */
    protected void addSpringBeansToCdi( @Observes final AfterBeanDiscovery abd, final BeanManager bm ) throws LuteceInitException
    {
        AppLogService.info( "Loading context files ..." );
        SpringContextService.initParentContext( );
        GenericWebApplicationContext ctx = (GenericWebApplicationContext) SpringContextService.getParentContext( );
        if ( ctx != null )
        {
            for ( String id : ctx.getBeanDefinitionNames( ) )
            {

                final Class<Object> clazz = (Class<Object>) ctx.getType( id );
                final AnnotatedType<Object> at = bm.createAnnotatedType( clazz );
                final InjectionTargetFactory<Object> injectionTargetFactory = bm.getInjectionTargetFactory( at );
                // final InjectionTarget<Object> injectionTarget= bm.createInjectionTarget(at);
                final InjectionTarget<Object> injectionTarget = injectionTargetFactory.createInjectionTarget( null );
                // abd.addBean(new SpringBean<Object>(ctx, clazz, id, injectionTarget.configure()..createInjectionTarget(null)));

                abd.addBean( ).beanClass( clazz ).name( id ).addInjectionPoints( injectionTarget.getInjectionPoints( ) ).addTypes( getAllSuperclasses( clazz ) )
                        .addQualifier( NamedLiteral.of( id ) ).createWith( tCreationalContext -> {
                            // Object instance = injectionTarget.produce(tCreationalContext);
                            Object instance = SpringContextService.getBean( id, clazz );
                            injectionTarget.inject( instance, tCreationalContext );
                            injectionTarget.postConstruct( instance );
                            return instance;
                        } ).destroyWith( ( instance, tCreationalContext ) -> {
                            injectionTarget.preDestroy( instance );
                            injectionTarget.dispose( instance );
                            tCreationalContext.release( );
                        } );
                // .produceWith(objet -> SpringContextService.getBean(id, clazz));
            }

        }
        else
        {
            AppLogService.error( "no spring application context found" );
        }
    }

    /**
     * Initialization of the Spring container.
     * 
     * @param context
     *            the servlet context
     * @throws LuteceInitException
     *             the LuteceInitException
     */
    protected void initializedSpringContext( @Observes @Initialized( ApplicationScoped.class ) @Priority( value = 2 ) ServletContext context )
            throws LuteceInitException
    {

        SpringContextService.init( context );
    }

    /**
     * Get a list type of the bean
     * 
     * @param clazz
     *            the class of bean
     * @return the list of beans type
     */
    private Set<Type> getAllSuperclasses( Class<?> clazz )
    {
        if ( clazz == null )
        {
            return null;
        }
        Set<Type> classes = new HashSet<>( );
        classes.add( clazz );
        Class<?> superclass = clazz.getSuperclass( );
        java.lang.reflect.AnnotatedType [ ] annotatedType = clazz.getAnnotatedInterfaces( );

        for ( java.lang.reflect.AnnotatedType type : annotatedType )
        {
            classes.add( type.getType( ) );
        }
        while ( superclass != null && superclass != Object.class )
        {
            classes.add( superclass );
            superclass = superclass.getSuperclass( );
        }
        return classes;
    }

}
