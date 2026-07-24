package com.projecteden.dataset;

import java.util.List;

public interface DatasetCollectionPlanManager {
	CollectionPlan createPlan(CreateCollectionPlanCommand command);
	CollectionPlan findPlan(String datasetId, String planId);
	List<CollectionPlan> listPlans(String datasetId);
	CollectionPlan updatePlan(String datasetId, String planId, UpdateCollectionPlanCommand command);
	CollectionPlan activatePlan(String datasetId, String planId);
	CollectionPlan completePlan(String datasetId, String planId);
	CollectionPlan archivePlan(String datasetId, String planId);
	CollectionCoverageReport generateCoverage(String datasetId, String planId);
}
