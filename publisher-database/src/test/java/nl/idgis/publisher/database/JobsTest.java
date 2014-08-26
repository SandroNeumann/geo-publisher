package nl.idgis.publisher.database;

import static nl.idgis.publisher.database.QCategory.category;
import static nl.idgis.publisher.database.QDataSource.dataSource;
import static nl.idgis.publisher.database.QJob.job;
import static nl.idgis.publisher.database.QImportJobColumn.importJobColumn;
import static nl.idgis.publisher.database.QSourceDataset.sourceDataset;
import static nl.idgis.publisher.database.QSourceDatasetVersionColumn.sourceDatasetVersionColumn;
import static nl.idgis.publisher.database.QSourceDatasetVersion.sourceDatasetVersion;
import static nl.idgis.publisher.database.QDataset.dataset;
import static nl.idgis.publisher.database.QDatasetColumn.datasetColumn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import nl.idgis.publisher.database.messages.CreateHarvestJob;
import nl.idgis.publisher.database.messages.CreateImportJob;
import nl.idgis.publisher.database.messages.CreateServiceJob;
import nl.idgis.publisher.database.messages.DatasetStatus;
import nl.idgis.publisher.database.messages.GetDatasetStatus;
import nl.idgis.publisher.database.messages.GetHarvestJobs;
import nl.idgis.publisher.database.messages.GetImportJobs;
import nl.idgis.publisher.database.messages.GetServiceJobs;
import nl.idgis.publisher.database.messages.HarvestJobInfo;
import nl.idgis.publisher.database.messages.ImportJobInfo;
import nl.idgis.publisher.database.messages.JobInfo;
import nl.idgis.publisher.database.messages.ServiceJobInfo;
import nl.idgis.publisher.database.messages.UpdateJobState;
import nl.idgis.publisher.domain.job.JobState;
import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.Type;
import nl.idgis.publisher.protocol.messages.Ack;
import nl.idgis.publisher.utils.TypedIterable;

import org.junit.Test;

import com.mysema.query.Tuple;

public class JobsTest extends AbstractDatabaseTest {	

	@Test
	public void testHarvestJob() throws Exception {
		
		insertDataSource();	
		
		Object result = ask(new CreateHarvestJob("testDataSource"));
		assertTrue(result instanceof Ack);
		
		Tuple t = query().from(job).singleResult(job.all());
		assertNotNull(t);
		assertEquals("HARVEST", t.get(job.type));
		
		result = ask(new GetHarvestJobs());		
		assertTrue(result instanceof List);
		
		List<HarvestJobInfo> jobs = (List<HarvestJobInfo>)result;
		assertFalse(jobs.isEmpty());
		
		HarvestJobInfo job = jobs.get(0);
		assertNotNull(job);
		
		assertEquals("testDataSource", job.getDataSourceId());
	}	

	private int insertDataSource() {
		return insert(dataSource)
			.set(dataSource.identification, "testDataSource")
			.set(dataSource.name, "My Test DataSource")
			.executeWithKey(dataSource.id);
	}
	
	@Test
	public void testImportAndServiceJob() throws Exception {
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
				.executeWithKey(dataset.id);
		
		for(int i = 0; i < 10; i++) {
			insert(datasetColumn)
				.set(datasetColumn.datasetId, datasetId)
				.set(datasetColumn.index, i)
				.set(datasetColumn.name, "test" + i)
				.set(datasetColumn.dataType, "GEOMETRY")
				.execute();
		}
		
		Object result = ask(new GetDatasetStatus());
		assertTrue(result instanceof TypedIterable);
		
		TypedIterable<?> typedIterable = (TypedIterable<?>)result;
		assertTrue(typedIterable.contains(DatasetStatus.class));
		
		Iterator<DatasetStatus> i = typedIterable.cast(DatasetStatus.class).iterator();
		assertTrue(i.hasNext());
		
		DatasetStatus datasetStatus = i.next();
		assertNotNull(datasetStatus);
		
		assertColumns(datasetStatus.getColumns());		
		assertColumns(datasetStatus.getSourceColumns());
		
		assertEquals("testDataset", datasetStatus.getDatasetId());		
		assertEquals("testSourceDataset", datasetStatus.getSourceDatasetId());
		assertEquals(testRevision, datasetStatus.getSourceRevision());
		
		// import* attributes should still be empty at this point
		assertNull(datasetStatus.getImportedColumns());
		assertNull(datasetStatus.getImportedSourceColumns());
		assertNull(datasetStatus.getImportedSourceDatasetId());
		assertNull(datasetStatus.getImportedSourceRevision());
		
		assertFalse(datasetStatus.isServiceCreated());
		
		assertFalse(i.hasNext());
		
		result = ask(new CreateImportJob("testDataset"));
		assertTrue(result instanceof Ack);
		
		Tuple t = query().from(job).singleResult(job.all());
		assertNotNull(t);
		assertEquals("IMPORT", t.get(job.type));
		
		t = query().from(importJobColumn)
				.orderBy(importJobColumn.index.asc())
				.singleResult(importJobColumn.all());
		assertNotNull(t);
		assertEquals(0, t.get(importJobColumn.index).intValue());
		assertEquals("test0", t.get(importJobColumn.name));
		assertEquals("GEOMETRY", t.get(importJobColumn.dataType));
		
		result = ask(new GetImportJobs());
		assertTrue(result instanceof List);
		
		List<JobInfo> jobsInfos = (List<JobInfo>)result;
		assertFalse(jobsInfos.isEmpty());
		
		JobInfo jobInfo = jobsInfos.get(0);
		assertTrue(jobInfo instanceof ImportJobInfo);
		
		ImportJobInfo importJobInfo = (ImportJobInfo)jobInfo;
		assertEquals("testDataset", importJobInfo.getDatasetId());
		assertEquals("testCategory", importJobInfo.getCategoryId());
		
		assertColumns(importJobInfo.getColumns());
		
		result = ask(new GetDatasetStatus());
		assertTrue(result instanceof TypedIterable);
		
		typedIterable = (TypedIterable<?>)result;
		assertTrue(typedIterable.contains(DatasetStatus.class));
		
		i = typedIterable.cast(DatasetStatus.class).iterator();
		assertTrue(i.hasNext());
		
		datasetStatus = i.next();
		assertNotNull(datasetStatus);
		
		// import* attributes should be populated now
		assertEquals(testRevision, datasetStatus.getImportedSourceRevision());
		assertEquals("testSourceDataset", datasetStatus.getImportedSourceDatasetId());
		
		assertColumns(datasetStatus.getImportedSourceColumns());				
		assertColumns(datasetStatus.getImportedColumns());		
		
		assertFalse(i.hasNext());
		
		result = ask(new UpdateJobState(jobInfo, JobState.SUCCEEDED));
		assertTrue(result instanceof Ack);
		
		result = ask(new GetDatasetStatus());
		assertTrue(result instanceof TypedIterable);
		
		typedIterable = (TypedIterable<?>) result;
		assertTrue(typedIterable.contains(DatasetStatus.class));
		
		i = typedIterable.cast(DatasetStatus.class).iterator();
		assertNotNull(i);
		assertTrue(i.hasNext());
		
		datasetStatus = i.next();
		assertFalse(datasetStatus.isServiceCreated());
		
		result = ask(new CreateServiceJob("testDataset"));
		assertTrue(result instanceof Ack);
		
		result = ask(new GetServiceJobs());
		assertTrue(result instanceof List);
		
		jobsInfos = (List<JobInfo>)result;
		assertFalse(jobsInfos.isEmpty());
		
		jobInfo = jobsInfos.get(0);
		assertTrue(jobInfo instanceof ServiceJobInfo);
		
		ServiceJobInfo serviceJobInfo = (ServiceJobInfo)jobInfo;
		result = ask(new UpdateJobState(serviceJobInfo, JobState.SUCCEEDED));
		assertTrue(result instanceof Ack);
		
		result = ask(new GetDatasetStatus());
		assertTrue(result instanceof TypedIterable);
		
		typedIterable = (TypedIterable<?>) result;
		assertTrue(typedIterable.contains(DatasetStatus.class));
		
		i = typedIterable.cast(DatasetStatus.class).iterator();
		assertNotNull(i);
		assertTrue(i.hasNext());
		
		datasetStatus = i.next();
		assertTrue(datasetStatus.isServiceCreated());
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