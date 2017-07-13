/**********************************************************************
Copyright (c) 2006 Andy Jefferson and others. All rights reserved. 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. 


Contributors:
    ...
**********************************************************************/
package org.datanucleus.store.rdbms.valuegenerator;

import java.util.Properties;

import org.datanucleus.store.StoreManager;
import org.datanucleus.store.connection.ManagedConnection;
import org.datanucleus.store.valuegenerator.AbstractConnectedGenerator;
import org.datanucleus.store.valuegenerator.ValueGenerationBlock;
import org.datanucleus.store.valuegenerator.ValueGenerationException;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.NucleusLogger;

/**
 * Abstract representation of a ValueGenerator for RDBMS datastores.
 * Builds on the AbstractConnectedGenerator, providing the idea of a repository of ids in the datastore.
 */
public abstract class AbstractRDBMSGenerator<T> extends AbstractConnectedGenerator<T>
{
    /** Connection to the datastore. */
    protected ManagedConnection connection;

    /** Flag for whether we know that the repository exists. Only applies if repository is required. */
    protected boolean repositoryExists = false;

    /**
     * Constructor.
     * @param storeMgr StoreManager
     * @param name Symbolic name for the generator
     * @param props Properties controlling the behaviour of the generator
     */
    public AbstractRDBMSGenerator(StoreManager storeMgr, String name, Properties props)
    {
        super(storeMgr, name, props);
        allocationSize = 1;
    }

    /**
     * Method to reply if the generator requires a connection.
     * @return Whether a connection is required.
     */
    public boolean requiresConnection()
    {
        return true;
    }

    /**
     * Indicator for whether the generator requires its own repository.
     * AbstractValueGenerator returns false and this should be overridden by all
     * generators requiring a repository.
     * @return Whether a repository is required.
     */
    protected boolean requiresRepository()
    {
        return false;
    }

    /**
     * Method to return if the repository already exists.
     * @return Whether the repository exists
     */
    protected boolean repositoryExists()
    {
        return true;
    }

    /**
     * Method to create any needed repository for the values.
     * AbstractValueGenerator just returns true and should be overridden by any
     * implementing generator requiring its own repository.
     * @return If all is ready for use
     */
    protected boolean createRepository()
    {
        // Do nothing - to be overridden by generators that want to create a repository for their ids
        return true;
    }

    /**
     * Get a new PoidBlock with the specified number of ids.
     * @param number The number of additional ids required
     * @return the PoidBlock
     */
    protected ValueGenerationBlock<T> obtainGenerationBlock(int number)
    {
        ValueGenerationBlock<T> block = null;

        // Try getting the block
        boolean repository_exists=true; // TODO Ultimately this can be removed when "repositoryExists()" is implemented
        try
        {
            if (requiresConnection())
            {
                connection = connectionProvider.retrieveConnection();
            }

            if (requiresRepository() && !repositoryExists)
            {
                // Make sure the repository is present before proceeding
                repositoryExists = repositoryExists();
                if (!repositoryExists)
                {
                    createRepository();
                    repositoryExists = true;
                }
            }

            try
            {
                if (number < 0)
                {
                    block = reserveBlock();
                }
                else
                {
                    block = reserveBlock(number);
                }
            }
            catch (ValueGenerationException vge)
            {
                NucleusLogger.VALUEGENERATION.info(Localiser.msg("040003", vge.getMessage()));
                if (NucleusLogger.VALUEGENERATION.isDebugEnabled())
                {
                    NucleusLogger.VALUEGENERATION.debug("Caught exception", vge);
                }

                // attempt to obtain the block of unique identifiers is invalid
                if (requiresRepository())
                {
                    repository_exists = false;
                }
                else
                {
                    throw vge;
                }
            }
            catch (RuntimeException ex)
            {
                NucleusLogger.VALUEGENERATION.info(Localiser.msg("040003", ex.getMessage()));
                if (NucleusLogger.VALUEGENERATION.isDebugEnabled())
                {
                    NucleusLogger.VALUEGENERATION.debug("Caught exception", ex);
                }

                // attempt to obtain the block of unique identifiers is invalid
                if (requiresRepository())
                {
                    repository_exists = false;
                }
                else
                {
                    throw ex;
                }
            }
        }
        finally
        {
            if (connection != null && requiresConnection())
            {
                connectionProvider.releaseConnection();
                connection = null;
            }
        }

        // If repository didn't exist, try creating it and then get block
        if (!repository_exists)
        {
            try
            {
                if (requiresConnection())
                {
                    connection = connectionProvider.retrieveConnection();
                }

                NucleusLogger.VALUEGENERATION.info(Localiser.msg("040005"));
                if (!createRepository())
                {
                    throw new ValueGenerationException(Localiser.msg("040002"));
                }

                if (number < 0)
                {
                    block = reserveBlock();
                }
                else
                {
                    block = reserveBlock(number);
                }
            }
            finally
            {
                if (requiresConnection())
                {
                    connectionProvider.releaseConnection();
                    connection = null;
                }
            }
        }
        return block;
    }
}