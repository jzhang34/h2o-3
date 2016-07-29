package water.fvec;

import water.*;
import water.util.UnsafeUtils;

import java.util.Arrays;

/**
 * Created by tomas on 7/8/16.
 * Generalized Vec. Can hold multiple columns.
 */
public class VecBlock extends AVec<ChunkBlock> {
  int _nVecs;
  int [] _removedVecs;

  String [][] _domains;

  public VecBlock(Key<AVec> key, int rowLayout, int nCols, String[][] domains, byte[] types) {
    super(key,rowLayout);
    _domains = domains;
    _types = types;
  }

  public String[] domain(int i){return _domains == null?null:_domains[i];}

  @Override
  public byte type(int colId) {
    return 0;
  }

  public boolean hasVec(int id) {
    if(_nVecs < id || id < 0) return false;
    return _removedVecs == null || Arrays.binarySearch(_removedVecs,id) < 0;
  }


  @Override
  public int numCols(){return _nVecs - (_removedVecs == null?0:_removedVecs.length);}

  @Override
  public boolean hasCol(int id) {
    if(numCols() < id || id < 0) return false;
    return _removedVecs == null || Arrays.binarySearch(_removedVecs,id) < 0;
  }

  public RollupStats getRollups(int vecId, boolean histo) {
    if(!hasVec(vecId)) throw new NullPointerException("vec has been removed");
    throw H2O.unimpl(); // TODO
  }

  @Override
  public void setBad(int colId) {

  }

  // Vec internal type: one of T_BAD, T_UUID, T_STR, T_NUM, T_CAT, T_TIME
  byte [] _types;                   // Vec Type


  private transient Key _rollupStatsKey;

  public Key rollupStatsKey() {
    if( _rollupStatsKey==null ) _rollupStatsKey=chunkKey(-2);
    return _rollupStatsKey;
  }

  @Override
  public void preWriting(int... colIds) {

  }

  @Override
  public Futures postWrite(Futures fs) {
    throw H2O.unimpl();
  }

  @Override
  public Futures closeChunk(int cidx, AChunk ac, Futures fs) {
    ChunkBlock cb = (ChunkBlock) ac;
    boolean modified = false;
    for(int i = 0; i < cb._chunks[i]._len; ++i) {
      Chunk c = cb._chunks[i];
      if(c._chk2 != null) {
        modified = true;
        if(c._chk2 instanceof NewChunk)
          cb._chunks[i] = ((NewChunk) c._chk2).compress();
      }
    }
    if(modified) DKV.put(chunkKey(cidx),cb,fs);
    return fs;
  }


  public long byteSize() { return 0; }



  public void close() {
    throw H2O.unimpl(); // TODO
  }

  public void setDomains(String[][] domains) {
    /** Set the categorical/factor names.  No range-checking on the actual
     *  underlying numeric domain; user is responsible for maintaining a mapping
     *  which is coherent with the Vec contents. */
    _domains = domains;
    for(int i = 0; i < domains.length; ++i)
      if( domains[i] != null ) assert _types[i] == Vec.T_CAT;
  }

  @Override
  public VecBlock doCopy(){
    final VecBlock v = new VecBlock(group().addVec(),_rowLayout,_nVecs,_domains,_types);
    new MRTask(){
      @Override public void map(Chunk [] chks){
        chks = chks.clone();
        for(int i = 0; i < chks.length; ++i)
          chks[i] = chks[i].deepCopy();
        DKV.put(v.chunkKey(chks[0].cidx()), new ChunkBlock(chks), _fs);
      }
    }.doAll(new VecAry(this));
    return v;
  }

}