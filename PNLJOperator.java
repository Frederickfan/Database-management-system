package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.common.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.io.Page;
import edu.berkeley.cs186.database.table.Record;

public class PNLJOperator extends JoinOperator {
    public PNLJOperator(QueryOperator leftSource,
                        QueryOperator rightSource,
                        String leftColumnName,
                        String rightColumnName,
                        Database.Transaction transaction) throws QueryPlanException, DatabaseException {
        super(leftSource,
              rightSource,
              leftColumnName,
              rightColumnName,
              transaction,
              JoinType.PNLJ);

        // for HW4
        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
        return new PNLJIterator();
    }

    public int estimateIOCost() throws QueryPlanException {
        //does nothing
        return 0;
    }

    /**
     * PNLJ: Page Nested Loop Join
     *  See lecture slides.
     *
     * An implementation of Iterator that provides an iterator interface for this operator.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might prove to be a useful reference).
     */
    private class PNLJIterator extends JoinIterator {
        /**
         * Some member variables are provided for guidance, but there are many possible solutions.
         * You should implement the solution that's best for you, using any member variables you need.
         * You're free to use these member variables, but you're not obligated to.
         */

        private Iterator<Page> leftIterator = null;
        private Iterator<Page> rightIterator = null;
        private BacktrackingIterator<Record> leftRecordIterator = null;
        private BacktrackingIterator<Record> rightRecordIterator = null;
        private Record leftRecord = null;
        private Record rightRecord = null;
        private Record nextRecord = null;
        private Page currentLeftPage = null;
        private Page currentRightPage = null;

        public PNLJIterator() throws QueryPlanException, DatabaseException {
            super();
            leftIterator = getPageIterator(getLeftTableName());
            rightIterator = getPageIterator(getRightTableName());

            leftIterator.next();
            rightIterator.next();

            leftRecordIterator = getBlockIterator(getLeftTableName(), leftIterator, 1);
            rightRecordIterator = getBlockIterator(getLeftTableName(), rightIterator, 1 );


            leftRecord = leftRecordIterator.next();
            rightRecord = rightRecordIterator.next();


            nextRecord = null;

            // We mark the first record so we can reset to it when we advance the left record.
            if (rightRecord != null) {
                rightRecordIterator.mark();
            } else { return; }

            if (leftRecord != null) {
                leftRecordIterator.mark();
            }

            try {
                fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }


        }

        /**
         * After this method is called, rightRecord will contain the first record in the rightSource.
         * There is always a first record. If there were no first records (empty rightSource)
         * then the code would not have made it this far.
         */
        private void resetRightRecord() {
            this.rightRecordIterator.reset();
            assert(rightRecordIterator.hasNext());
            rightRecord = rightRecordIterator.next();
            rightRecordIterator.mark();
        }

        private void resetLeftRecord() {
            this.leftRecordIterator.reset();
            assert(leftRecordIterator.hasNext());
            leftRecord = leftRecordIterator.next();
            leftRecordIterator.mark();
        }

        private void advanceRightIter() {
            if ( rightRecordIterator.hasNext() ) {
                rightRecord = rightRecordIterator.next();
            } else {
                rightRecord = null;
            }
        }

        private void advanceLeftIter() {
            if ( leftRecordIterator.hasNext() ) {
                leftRecord = leftRecordIterator.next();
            } else {
                leftRecord = null;
            }
        }

        private void fetchNextRecord() throws DatabaseException {
            if ( this.leftRecord == null ) {
                throw new DatabaseException( "No new record to fetch" );
            }
            this.nextRecord = null;
            do {
                if (rightRecord != null ) {
                    DataBox leftJoinValue = this.leftRecord.getValues().get( PNLJOperator.this.getLeftColumnIndex() );
                    DataBox rightJoinValue = rightRecord.getValues().get( PNLJOperator.this.getRightColumnIndex() );
                    if ( leftJoinValue.equals(rightJoinValue)) {
                        List<DataBox> leftValues = new ArrayList<>( this.leftRecord.getValues() );
                        List<DataBox> rightValues = new ArrayList<>( rightRecord.getValues() );
                        leftValues.addAll( rightValues );
                        this.nextRecord = new Record( leftValues );
                    }
                    advanceRightIter();
                } else if ( leftRecordIterator.hasNext() ) {
                    leftRecord = leftRecordIterator.next();
                    resetRightRecord();
                } else if ( rightIterator.hasNext() ) {
                    rightRecordIterator = PNLJOperator.this.getBlockIterator( getRightTableName(), this.rightIterator, 1 );
                    advanceRightIter();
                    rightRecordIterator.mark();
                    resetLeftRecord();
                } else if ( leftIterator.hasNext() ) {
                    leftRecordIterator = PNLJOperator.this.getBlockIterator( getLeftTableName(), this.leftIterator, 1 );
                    advanceLeftIter();
                    leftRecordIterator.mark();

                    /** reset right record iterator for the next left page. */
                    rightIterator = PNLJOperator.this.getPageIterator( this.getRightTableName() );

                    /** initiate the right page */
                    rightIterator.next();

                    /** initiate the right record */
                    rightRecordIterator = PNLJOperator.this.getBlockIterator( this.getRightTableName(), this.rightIterator, 1 );
                    advanceRightIter();
                    rightRecordIterator.mark();
                } else {
                    throw new DatabaseException( "No new record available" );
                }

            } while ( !hasNext() );
        }

        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        public boolean hasNext() {
            return nextRecord != null;
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */
        public Record next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            Record nextRecord = this.nextRecord;
            try {
                this.fetchNextRecord();
            } catch (DatabaseException e) {
                this.nextRecord = null;
            }
            return nextRecord;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}

