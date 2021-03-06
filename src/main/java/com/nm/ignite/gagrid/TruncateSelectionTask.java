package com.nm.ignite.gagrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cache.Cache.Entry;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeJobResultPolicy;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.resources.IgniteInstanceResource;

import com.nm.ignite.gagrid.parameter.GAConfiguration;
import com.nm.ignite.gagrid.parameter.GAGridConstants;


/**
*
* 
* Responsible for performing truncate selection.
* 
* 
* 
* @author turik.campbell
*
*/

public class TruncateSelectionTask extends ComputeTaskAdapter<List<Long>, Boolean> {

    @IgniteInstanceResource
    private Ignite ignite = null;

    private GAConfiguration config = null;

    private List<Long> fittestKeys = null;

    private int numberOfCopies = 0;

    /**
     * 
     * @param GAConfiguration
     */
    public TruncateSelectionTask(GAConfiguration config, List<Long> fittestKeys, int numberOfCopies) {
        this.config = config;
        this.fittestKeys = fittestKeys;
        this.numberOfCopies = numberOfCopies;
    }

    /**
     * @param ClusterNode
     * @param List<Long>   - primary keys for respective chromosomes
     *          
     */
    public Map map(List<ClusterNode> nodes, List<Long> chromosomeKeys) throws IgniteException {
        Map<ComputeJob, ClusterNode> map = new HashMap<>();
        Affinity affinity = ignite.affinity(GAGridConstants.POPULATION_CACHE);

        // Retrieve enhanced population
        List<List<Long>> enhancedPopulation = getEnhancedPopulation();

        int k = 0;
        for (Long key : chromosomeKeys) {
            TruncateSelectionJob ajob = new TruncateSelectionJob(key, (List) enhancedPopulation.get(k));
            ClusterNode primary = affinity.mapKeyToNode(key);
            map.put(ajob, primary);
            k = k + 1;
        }
        return map;

    }

    /**
     * We return TRUE if success, else Exception is thrown.
     * 
     * @param List<ComputeJobResult>
     * @return TRUE
     */
    public Boolean reduce(List<ComputeJobResult> arg0) throws IgniteException {
        // TODO Auto-generated method stub
        return Boolean.TRUE;
    }

    private List<List<Long>> getEnhancedPopulation() {
        List<List<Long>> list = new ArrayList();

        for (Long key : fittestKeys) {
            Chromosome copy = getChromosome(key);
            for (int i = 0; i < numberOfCopies; i++) {
                long[] thegenes = copy.getGenes();
                List<Long> geneList = new ArrayList();
                for (int k = 0; k < copy.getGenes().length; k++) {
                    geneList.add(thegenes[k]);
                }
                list.add(geneList);
            }
        }

        return list;
    }

    private Chromosome getChromosome(Long key) {
        IgniteCache<Long, Chromosome> cache = ignite.cache(GAGridConstants.POPULATION_CACHE);
        StringBuffer sbSqlClause = new StringBuffer();
        sbSqlClause.append("_key IN (");
        sbSqlClause.append(key);
        sbSqlClause.append(")");

        Chromosome chromosome = null;

        SqlQuery sql = new SqlQuery(Chromosome.class, sbSqlClause.toString());

        try (QueryCursor<Entry<Long, Chromosome>> cursor = cache.query(sql)) {
            for (Entry<Long, Chromosome> e : cursor)
                chromosome = (e.getValue())

                ;
        }

        return chromosome;
    }
    
    /**
     * @param ComputeJobResult res
     * @param  List<ComputeJobResult> rcvd
     * 
     * @return ComputeJobResultPolicy
     */
    public ComputeJobResultPolicy result(ComputeJobResult res, List<ComputeJobResult> rcvd) {
        IgniteException err = res.getException();
  
        
        if (err != null)
            return ComputeJobResultPolicy.FAILOVER;
    
        // If there is no exception, wait for all job results.
        return ComputeJobResultPolicy.WAIT;
   
    }

}
