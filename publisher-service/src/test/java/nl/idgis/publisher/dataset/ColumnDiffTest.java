package nl.idgis.publisher.dataset;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import nl.idgis.publisher.AbstractServiceTest;

import nl.idgis.publisher.database.messages.CreateDataset;
import nl.idgis.publisher.database.messages.UpdateDataset;

import nl.idgis.publisher.dataset.messages.RegisterSourceDataset;
import nl.idgis.publisher.dataset.messages.Updated;

import nl.idgis.publisher.domain.Log;
import nl.idgis.publisher.domain.service.Column;
import nl.idgis.publisher.domain.service.VectorDataset;
import nl.idgis.publisher.domain.service.Table;
import nl.idgis.publisher.domain.service.Type;

import nl.idgis.publisher.job.manager.messages.CreateImportJob;
import nl.idgis.publisher.job.manager.messages.GetImportJobs;

import org.junit.Before;
import org.junit.Test;

import com.mysema.query.Tuple;

import static nl.idgis.publisher.database.QDataSource.dataSource;
import static nl.idgis.publisher.database.QDatasetColumnDiff.datasetColumnDiff;
import static nl.idgis.publisher.database.QSourceDatasetColumnDiff.sourceDatasetColumnDiff;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ColumnDiffTest extends AbstractServiceTest {
	
	VectorDataset testSourceDataset;
	Table testTable;
	List<Column> testColumns;

	@Before
	public void createDataset() throws Exception {
		insert(dataSource)
			.set(dataSource.identification, "testDataSource")
			.set(dataSource.name, "My Test Dataset")
			.execute();
	
		testSourceDataset = createVectorDataset();
		testTable = testSourceDataset.getTable();
		testColumns = testTable.getColumns();
		sync.ask(datasetManager, new RegisterSourceDataset("testDataSource", testSourceDataset));
		
		sync.ask(database, new CreateDataset(
			"testDataset", 
			"My Test Dataset", 
			testSourceDataset.getId(),			
			testTable.getColumns(), 
			""));
	}

	@Test
	public void testDataset() throws Exception {
		// not yet imported, changes are calculated between currently configured 
		// columns and last imported columns 
		assertFalse(query().from(datasetColumnDiff).exists());
		
		sync.ask(jobManager, new CreateImportJob("testDataset"));
		executeJobs(new GetImportJobs());
		
		// no changes yet
		assertFalse(query().from(datasetColumnDiff).exists());
		
		sync.ask(database,
			new UpdateDataset(
				"testDataset", 
				"My Test Dataset", 
				"testVectorDataset", 
				Arrays.asList(testColumns.get(0)), // removes second column 
				""));
		
		List<Tuple> tuples = 
				query().from(datasetColumnDiff)
				.list(datasetColumnDiff.all());
		
		Iterator<Tuple> itr = tuples.iterator();
		assertTrue(itr.hasNext());
		
		Tuple tuple = itr.next();
		assertEquals("REMOVE", tuple.get(datasetColumnDiff.diff));
		
		Column expectedRemoved = testColumns.get(1);
		assertEquals(expectedRemoved.getName(), tuple.get(datasetColumnDiff.name));
		assertEquals(expectedRemoved.getDataType().name(), tuple.get(datasetColumnDiff.dataType));
		
		assertFalse(itr.hasNext());
		
		sync.ask(jobManager, new CreateImportJob("testDataset"));
		executeJobs(new GetImportJobs());
		
		// dataset updated
		assertFalse(query().from(datasetColumnDiff).exists());
		
		Column newColumn = new Column("my_new_column", Type.GEOMETRY);		
		sync.ask(database, 
				new UpdateDataset(
					"testDataset", 
					"My Test Dataset", 
					"testVectorDataset", 
					Arrays.asList(
						testColumns.get(0),
						newColumn), // add new column
					""));
		
		tuples = 
			query().from(datasetColumnDiff)
			.list(datasetColumnDiff.all());
		
		itr = tuples.iterator();
		assertTrue(itr.hasNext());
		
		tuple = itr.next();
		assertEquals("ADD", tuple.get(datasetColumnDiff.diff));
		
		assertEquals(newColumn.getName(), tuple.get(datasetColumnDiff.name));
		assertEquals(newColumn.getDataType().name(), tuple.get(datasetColumnDiff.dataType));
		
		assertFalse(itr.hasNext());
	}
	
	@Test
	public void testSourceDataset() throws Exception {
		// not yet imported, changes are calculated between last source dataset 
		// version and last imported source dataset version
		assertFalse(query().from(sourceDatasetColumnDiff).exists());
		
		sync.ask(jobManager, new CreateImportJob("testDataset"));
		executeJobs(new GetImportJobs());
		
		// no changes yet
		assertFalse(query().from(sourceDatasetColumnDiff).exists());
		
		Table newTable = new Table(Arrays.asList(testColumns.get(0))); // removes second column
		VectorDataset newSourceDataset = new VectorDataset(
			testSourceDataset.getId(),
			testSourceDataset.getName(),
			testSourceDataset.getCategoryId(),			
			testSourceDataset.getRevisionDate(),
			Collections.<Log>emptySet(),
			newTable);
		
		sync.ask(datasetManager, new RegisterSourceDataset("testDataSource", newSourceDataset), Updated.class);
		
		List<Tuple> tuples = query().from(sourceDatasetColumnDiff)
			.list(sourceDatasetColumnDiff.all());
		Iterator<Tuple> itr = tuples.iterator();
		assertTrue(itr.hasNext());
		
		Tuple tuple = itr.next();
		assertEquals("REMOVE", tuple.get(sourceDatasetColumnDiff.diff));
		
		Column expectedRemoved = testColumns.get(1);
		assertEquals(expectedRemoved.getName(), tuple.get(sourceDatasetColumnDiff.name));
		assertEquals(expectedRemoved.getDataType().name(), tuple.get(sourceDatasetColumnDiff.dataType));
		
		assertFalse(itr.hasNext());
		
		sync.ask(jobManager, new CreateImportJob("testDataset"));
		executeJobs(new GetImportJobs());
		
		// dataset updated
		assertFalse(query().from(sourceDatasetColumnDiff).exists());
		
		Column newColumn = new Column("additional_column", Type.DATE);
		newTable = new Table(Arrays.asList(testColumns.get(0), newColumn));
		newSourceDataset = new VectorDataset(
			testSourceDataset.getId(),
			testSourceDataset.getName(),
			testSourceDataset.getCategoryId(),			
			testSourceDataset.getRevisionDate(),
			Collections.<Log>emptySet(),
			newTable);
		
		sync.ask(datasetManager, new RegisterSourceDataset("testDataSource", newSourceDataset), Updated.class);
		
		tuples = query().from(sourceDatasetColumnDiff)
				.list(sourceDatasetColumnDiff.all());
		itr = tuples.iterator();
		assertTrue(itr.hasNext());
		
		tuple = itr.next();
		assertEquals("ADD", tuple.get(sourceDatasetColumnDiff.diff));
		
		assertEquals(newColumn.getName(), tuple.get(sourceDatasetColumnDiff.name));
		assertEquals(newColumn.getDataType().name(), tuple.get(sourceDatasetColumnDiff.dataType));
		
		assertFalse(itr.hasNext());
		
		sync.ask(jobManager, new CreateImportJob("testDataset"));
		executeJobs(new GetImportJobs());
		
		assertFalse(query().from(sourceDatasetColumnDiff).exists());
	}
}
