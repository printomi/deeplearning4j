package org.datavec.api.records.reader.impl.transform;

import lombok.AllArgsConstructor;
import org.datavec.api.conf.Configuration;
import org.datavec.api.records.Record;
import org.datavec.api.records.listener.RecordListener;
import org.datavec.api.records.metadata.RecordMetaData;
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.split.InputSplit;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.writable.Writable;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * This wraps a {@link RecordReader}
 * with a {@link TransformProcess} and allows every {@link Record}
 * that is returned by the {@link RecordReader}
 * to have a transform process applied before being returned.
 *
 * @author Adam Gibson
 */
@AllArgsConstructor
public class TransformProcessRecordReader implements RecordReader {
    private RecordReader recordReader;
    private TransformProcess transformProcess;

    /**
     * Called once at initialization.
     *
     * @param split the split that defines the range of records to read
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void initialize(InputSplit split) throws IOException, InterruptedException {

    }

    /**
     * Called once at initialization.
     *
     * @param conf  a configuration for initialization
     * @param split the split that defines the range of records to read
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void initialize(Configuration conf, InputSplit split) throws IOException, InterruptedException {

    }

    /**
     * Get the next record
     *
     * @return
     */
    @Override
    public List<Writable> next() {
        return transformProcess.execute(recordReader.next());
    }

    /**
     * Whether there are anymore records
     *
     * @return
     */
    @Override
    public boolean hasNext() {
        return recordReader.hasNext();
    }

    /**
     * List of label strings
     *
     * @return
     */
    @Override
    public List<String> getLabels() {
        return recordReader.getLabels();
    }

    /**
     * Reset record reader iterator
     *
     * @return
     */
    @Override
    public void reset() {
        recordReader.reset();
    }

    /**
     * Load the record from the given DataInputStream
     * Unlike {@link #next()} the internal state of the RecordReader is not modified
     * Implementations of this method should not close the DataInputStream
     *
     * @param uri
     * @param dataInputStream
     * @throws IOException if error occurs during reading from the input stream
     */
    @Override
    public List<Writable> record(URI uri, DataInputStream dataInputStream) throws IOException {
        return transformProcess.execute(recordReader.record(uri, dataInputStream));
    }

    /**
     * Similar to {@link #next()}, but returns a {@link Record} object, that may include metadata such as the source
     * of the data
     *
     * @return next record
     */
    @Override
    public Record nextRecord() {
        Record next = recordReader.nextRecord();
        return new org.datavec.api.records.impl.Record(transformProcess.execute(next.getRecord()), next.getMetaData());
    }

    /**
     * Load a single record from the given {@link RecordMetaData} instance<br>
     * Note: that for data that isn't splittable (i.e., text data that needs to be scanned/split), it is more efficient to
     * load multiple records at once using {@link #loadFromMetaData(List)}
     *
     * @param recordMetaData Metadata for the record that we want to load from
     * @return Single record for the given RecordMetaData instance
     * @throws IOException If I/O error occurs during loading
     */
    @Override
    public Record loadFromMetaData(RecordMetaData recordMetaData) throws IOException {
        return recordReader.loadFromMetaData(recordMetaData);
    }

    /**
     * Load multiple records from the given a list of {@link RecordMetaData} instances<br>
     *
     * @param recordMetaDatas Metadata for the records that we want to load from
     * @return Multiple records for the given RecordMetaData instances
     * @throws IOException If I/O error occurs during loading
     */
    @Override
    public List<Record> loadFromMetaData(List<RecordMetaData> recordMetaDatas) throws IOException {
        return recordReader.loadFromMetaData(recordMetaDatas);
    }

    /**
     * Get the record listeners for this record reader.
     */
    @Override
    public List<RecordListener> getListeners() {
        return recordReader.getListeners();
    }

    /**
     * Set the record listeners for this record reader.
     *
     * @param listeners
     */
    @Override
    public void setListeners(RecordListener... listeners) {
        recordReader.setListeners(listeners);
    }

    /**
     * Set the record listeners for this record reader.
     *
     * @param listeners
     */
    @Override
    public void setListeners(Collection<RecordListener> listeners) {
        recordReader.setListeners(listeners);

    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     * <p>
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        recordReader.close();
    }

    /**
     * Set the configuration to be used by this object.
     *
     * @param conf
     */
    @Override
    public void setConf(Configuration conf) {

    }

    /**
     * Return the configuration used by this object.
     */
    @Override
    public Configuration getConf() {
        return recordReader.getConf();
    }
}
