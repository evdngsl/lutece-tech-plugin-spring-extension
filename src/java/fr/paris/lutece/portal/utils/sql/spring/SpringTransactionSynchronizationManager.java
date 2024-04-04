package fr.paris.lutece.portal.utils.sql.spring;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import fr.paris.lutece.util.sql.ITransactionSynchronizationManager;
import fr.paris.lutece.util.sql.TransactionSynchronizationContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SpringTransactionSynchronizationManager implements ITransactionSynchronizationManager
{

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSynchronizationActive( )
    {
        return TransactionSynchronizationManager.isSynchronizationActive( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionSynchronizationContext registerSynchronization( TransactionSynchronizationContext context )
    {
        context.complete( DataSourceUtils.getConnection( context.getDataSource( ) ), false );
        return context;
    }
}
