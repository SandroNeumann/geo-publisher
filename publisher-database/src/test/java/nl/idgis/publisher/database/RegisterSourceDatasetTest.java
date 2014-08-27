package nl.idgis.publisher.database;

import static nl.idgis.publisher.database.QCategory.category;
import static nl.idgis.publisher.database.QDataSource.dataSource;
import static nl.idgis.publisher.database.QSourceDataset.sourceDataset;
import static nl.idgis.publisher.database.QSourceDatasetVersion.sourceDatasetVersion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.Iterator;

import nl.idgis.publisher.database.messages.AlreadyRegistered;
import nl.idgis.publisher.database.messages.RegisterSourceDataset;
import nl.idgis.publisher.database.messages.Registered;
import nl.idgis.publisher.database.messages.Updated;
import nl.idgis.publisher.domain.service.Dataset;

import org.junit.Test;

public class RegisterSourceDatasetTest extends AbstractDatabaseTest {

	@Test
	public void testRegisterNew() throws Exception {		 		
		insert(dataSource)
			.set(dataSource.identification, "testDataSource")
			.set(dataSource.name, "My Test DataSource")
			.execute();
		 
		Object result = ask(new RegisterSourceDataset("testDataSource", createTestDataset()));
		assertEquals(Registered.class, result.getClass());
		
		assertTrue(
			query().from(category)
			.where(category.identification.eq("testCategory"))
			.exists());
		
		assertTrue(
			query().from(sourceDataset)
				.where(sourceDataset.identification.eq("testSourceDataset"))
				.exists());
		
		assertEquals(1,
			query().from(sourceDatasetVersion)
				.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
				.where(sourceDataset.identification.eq("testSourceDataset"))
				.singleResult(sourceDatasetVersion.id.count()).intValue());
	}
	
	@Test
	public void testRegisterUpdate() throws Exception {
		insert(dataSource)
			.set(dataSource.identification, "testDataSource")
			.set(dataSource.name, "My Test DataSource")
			.execute();	 
		
		// fill database with other source datasets
		for(int i = 0; i < 100; i++) {
			Object result = ask(new RegisterSourceDataset("testDataSource", createTestDataset("otherSourceDataset" + i)));
			assertEquals(Registered.class, result.getClass());
		}
		
		Dataset dataset = createTestDataset();		
		Object result = ask(new RegisterSourceDataset("testDataSource", dataset));
		assertEquals(Registered.class, result.getClass());
		
		result = ask(new RegisterSourceDataset("testDataSource", dataset));
		assertEquals(AlreadyRegistered.class, result.getClass());
		
		Thread.sleep(1000); // createTestDataset() uses current time as revision date
		
		Dataset updatedDataset = createTestDataset();
		result = ask(new RegisterSourceDataset("testDataSource", updatedDataset));
		assertEquals(Updated.class, result.getClass());
		
		assertEquals(2,
				query().from(sourceDatasetVersion)
					.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
					.where(sourceDataset.identification.eq("testSourceDataset"))
					.singleResult(sourceDatasetVersion.id.count()).intValue());
		
		Iterator<Timestamp> itr = query().from(sourceDatasetVersion)
			.join(sourceDataset).on(sourceDataset.id.eq(sourceDatasetVersion.sourceDatasetId))
			.where(sourceDataset.identification.eq("testSourceDataset"))
			.orderBy(sourceDatasetVersion.id.asc())
			.list(sourceDatasetVersion.revision).iterator();
		
		assertTrue(itr.hasNext());
		
		Timestamp t = itr.next();
		assertEquals(dataset.getRevisionDate().getTime(), t.getTime());
		assertNotNull(t);
		
		assertTrue(itr.hasNext());
		t = itr.next();
		assertEquals(updatedDataset.getRevisionDate().getTime(), t.getTime());
		assertNotNull(t);
		
		assertFalse(itr.hasNext());
	}
}
