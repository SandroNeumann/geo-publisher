package nl.idgis.publisher.job;

import static nl.idgis.publisher.database.QCategory.category;
import static nl.idgis.publisher.database.QDataset.dataset;
import static nl.idgis.publisher.database.QDatasetColumn.datasetColumn;
import static nl.idgis.publisher.database.QImportJob.importJob;
import static nl.idgis.publisher.database.QImportJobColumn.importJobColumn;
import static nl.idgis.publisher.database.QJob.job;
import static nl.idgis.publisher.database.QRemoveJob.removeJob;
import static nl.idgis.publisher.database.QSourceDataset.sourceDataset;
import static nl.idgis.publisher.database.QSourceDatasetVersion.sourceDatasetVersion;
import static nl.idgis.publisher.database.QSourceDatasetVersionColumn.sourceDatasetVersionColumn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.mysema.query.Tuple;

import nl.idgis.publisher.AbstractServiceTest;

import nl.idgis.publisher.database.messages.DatasetStatusInfo;
import nl.idgis.publisher.database.messages.GetDatasetStatus;

import nl.idgis.publisher.domain.job.JobState;
import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.Type;

import nl.idgis.publisher.job.manager.messages.CreateHarvestJob;
import nl.idgis.publisher.job.manager.messages.CreateImportJob;
import nl.idgis.publisher.job.manager.messages.CreateRemoveJob;
import nl.idgis.publisher.job.manager.messages.GetHarvestJobs;
import nl.idgis.publisher.job.manager.messages.GetImportJobs;
import nl.idgis.publisher.job.manager.messages.GetRemoveJobs;
import nl.idgis.publisher.job.manager.messages.HarvestJobInfo;
import nl.idgis.publisher.job.manager.messages.ImportJobInfo;
import nl.idgis.publisher.job.manager.messages.RemoveJobInfo;
import nl.idgis.publisher.job.manager.messages.UpdateState;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.utils.TypedIterable;
import nl.idgis.publisher.utils.TypedList;

public class JobManagerTest extends AbstractServiceTest {
	
	@Test
	public void testRemoveJob() throws Exception {
		insertDataset();
		
		assertFalse(query().from(removeJob).exists());		
		sync.ask(jobManager, new CreateRemoveJob("testDataset"), Ack.class);
		assertTrue(query().from(removeJob).exists());
		
		TypedList<?> list = sync.ask(jobManager, new GetRemoveJobs(), TypedList.class);
		assertTrue(list.contains(RemoveJobInfo.class));
		
		Iterator<RemoveJobInfo> itr = list.cast(RemoveJobInfo.class).iterator();
		assertTrue(itr.hasNext());
		assertEquals("testDataset", itr.next().getDatasetId());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testHarvestJob() throws Exception {
		
		insertDataSource();	
		
		Object result = sync.ask(jobManager, new CreateHarvestJob("testDataSource"));
		assertTrue(result instanceof Ack);
		
		Tuple t = query().from(job).singleResult(job.all());
		assertNotNull(t);
		assertEquals("HARVEST", t.get(job.type));
		
		TypedIterable<?> jobs = sync.ask(jobManager, new GetHarvestJobs(), TypedIterable.class);		
		assertTrue(jobs.contains(HarvestJobInfo.class));
		
		Iterator<HarvestJobInfo> jobsItr = jobs.cast(HarvestJobInfo.class).iterator();
		assertTrue(jobsItr.hasNext());
		
		HarvestJobInfo job = jobsItr.next();
		assertNotNull(job);
		
		assertEquals("testDataSource", job.getDataSourceId());
		
		assertFalse(jobsItr.hasNext());
	}	
	
	@Test
	public void testImportJob() throws Exception {
		int dataSourceId = insertDataSource();
		
		int sourceDatasetId = 
			insert(sourceDataset)
				.set(sourceDataset.dataSourceId, dataSourceId)
				.set(sourceDataset.identification, "testSourceDataset")
				.executeWithKey(sourceDataset.id);
		
		int categoryId =
			insert(category)
				.set(category.identification, "testCategory")
				.set(category.name, "My Test Category")
				.executeWithKey(category.id);
		
		Timestamp testRevision = new Timestamp(new Date().getTime());
		
		int versionId =
			insert(sourceDatasetVersion)
				.set(sourceDatasetVersion.name, "My Test SourceDataset")
				.set(sourceDatasetVersion.type, "VECTOR")
				.set(sourceDatasetVersion.revision, testRevision)
				.set(sourceDatasetVersion.sourceDatasetId, sourceDatasetId)
				.set(sourceDatasetVersion.categoryId, categoryId)
				.executeWithKey(sourceDatasetVersion.id);
		
		for(int i = 0; i < 10; i++) {
			insert(sourceDatasetVersionColumn)
				.set(sourceDatasetVersionColumn.sourceDatasetVersionId, versionId)
				.set(sourceDatasetVersionColumn.index, i)
				.set(sourceDatasetVersionColumn.name, "test" + i)
				.set(sourceDatasetVersionColumn.dataType, "GEOMETRY")
				.execute();
		}
		
		int datasetId = 
			insert(dataset)
				.set(dataset.name, "My Test Dataset")
				.set(dataset.identification, "testDataset")
				.set(dataset.sourceDatasetId, sourceDatasetId)
				.set(dataset.uuid, UUID.randomUUID().toString())
				.set(dataset.fileUuid, UUID.randomUUID().toString())
				.executeWithKey(dataset.id);
		
		for(int i = 0; i < 10; i++) {
			insert(datasetColumn)
				.set(datasetColumn.datasetId, datasetId)
				.set(datasetColumn.index, i)
				.set(datasetColumn.name, "test" + i)
				.set(datasetColumn.dataType, "GEOMETRY")
				.execute();
		}
		
		Object result = sync.ask(database, new GetDatasetStatus());
		assertTrue(result instanceof TypedIterable);
		
		TypedIterable<?> typedIterable = (TypedIterable<?>)result;
		assertTrue(typedIterable.contains(DatasetStatusInfo.class));
		
		Iterator<DatasetStatusInfo> i = typedIterable.cast(DatasetStatusInfo.class).iterator();
		assertTrue(i.hasNext());
		
		DatasetStatusInfo datasetStatus = i.next();
		assertNotNull(datasetStatus);
		
		assertEquals("testDataset", datasetStatus.getDatasetId());
		assertFalse(datasetStatus.isImported());
		
		assertFalse(i.hasNext());
		
		result = sync.ask(jobManager, new CreateImportJob("testDataset"));
		assertTrue(result instanceof Ack);
		
		Tuple t = query().from(job).singleResult(job.all());
		assertNotNull(t);
		assertEquals("IMPORT", t.get(job.type));
		
		t = query().from(importJob).singleResult(importJob.all());
		assertNotNull(importJob.filterConditions);
		
		t = query().from(importJobColumn)
				.orderBy(importJobColumn.index.asc())
				.singleResult(importJobColumn.all());
		assertNotNull(t);
		assertEquals(0, t.get(importJobColumn.index).intValue());
		assertEquals("test0", t.get(importJobColumn.name));
		assertEquals("GEOMETRY", t.get(importJobColumn.dataType));
		
		TypedIterable<?> importJobsInfos = sync.ask(jobManager, new GetImportJobs(), TypedIterable.class);
		assertTrue(importJobsInfos.contains(ImportJobInfo.class));
		
		Iterator<ImportJobInfo> importJobsItr = importJobsInfos.cast(ImportJobInfo.class).iterator();
		assertTrue(importJobsItr.hasNext());
		
		ImportJobInfo importJobInfo = importJobsItr.next();
		assertEquals("testDataset", importJobInfo.getDatasetId());
		assertEquals("testCategory", importJobInfo.getCategoryId());		
		
		String filterCondition = importJobInfo.getFilterCondition();
		assertNotNull(filterCondition);
		
		assertColumns(importJobInfo.getColumns());
		
		result = sync.ask(database, new GetDatasetStatus());
		assertTrue(result instanceof TypedIterable);
		
		typedIterable = (TypedIterable<?>)result;
		assertTrue(typedIterable.contains(DatasetStatusInfo.class));
		
		i = typedIterable.cast(DatasetStatusInfo.class).iterator();
		assertTrue(i.hasNext());
		
		datasetStatus = i.next();
		assertNotNull(datasetStatus);
		assertFalse(datasetStatus.isImported());
		assertFalse(datasetStatus.isSourceDatasetColumnsChanged());
		assertFalse(i.hasNext());
		
		result = sync.ask(jobManager, new UpdateState(importJobInfo, JobState.SUCCEEDED));
		assertTrue(result instanceof Ack);
		
		result = sync.ask(database, new GetDatasetStatus());
		assertTrue(result instanceof TypedIterable);
		
		typedIterable = (TypedIterable<?>) result;
		assertTrue(typedIterable.contains(DatasetStatusInfo.class));
		
		i = typedIterable.cast(DatasetStatusInfo.class).iterator();
		assertNotNull(i);
		assertTrue(i.hasNext());
		
		datasetStatus = i.next();
	}

	private void assertColumns(List<Column> columns) {		
		assertNotNull(columns);
		
		assertEquals(10, columns.size());
		
		for(int i = 0; i < 10; i++) {
			Column column = columns.get(i);
			assertNotNull(column);
		
			assertEquals("test" + i, column.getName());
			assertEquals(Type.GEOMETRY, column.getDataType());
		}
	}
	
}
