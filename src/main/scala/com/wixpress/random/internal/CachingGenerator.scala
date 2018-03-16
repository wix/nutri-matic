package com.wixpress.random.internal

import com.google.common.cache.{Cache, CacheBuilder}
import com.wixpress.random
import com.wixpress.random.{Generator, TypeAndRandom}

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

private[random] trait CachingGenerator[Key <: AnyRef, Value] extends Generator[Value] {
  protected def hasCached(tc: TypeAndRandom): Boolean

  def orElseCached(other: CachingGenerator[Key, Value]): CachingGenerator[Key, Value] = new CachingGenerator[Key, Value] {
    override def hasCached(tc: (universe.Type, random.Context)): Boolean = CachingGenerator.this.hasCached(tc) || other.hasCached(tc)

    override def isDefinedAt(x: (universe.Type, random.Context)): Boolean = CachingGenerator.this.isDefinedAt(x) || other.isDefinedAt(x)

    override def apply(tc: (universe.Type, random.Context)): Value = {
      if (CachingGenerator.this.hasCached(tc)) {
        CachingGenerator.this.apply(tc)
      } else if (other.hasCached(tc)) {
        other.apply(tc)
      } else {
        CachingGenerator.this.applyOrElse(tc, other.apply)
      }
    }
  }

  def onlyIfCached: Generator[Value] = new Generator[Value] {

    override def isDefinedAt(x: (universe.Type, random.Context)): Boolean = CachingGenerator.this.hasCached(x)

    override def apply(v1: (universe.Type, random.Context)): Value = CachingGenerator.this.apply(v1)
  }
}

private[random] object CachingGenerator {
  def apply[T](generators: Seq[Generator[T]], keyFromType: Type => Type = identity, maxCacheSize: Int): CachingGenerator[Type, T] =
    new ByKeyLookupCachingGenerator[Type, T](
      generators = generators,
      keyFromType = keyFromType,
      maxCacheSize = maxCacheSize
    )

  def fromGeneratorsOfGenerators[T](generatorGenerators: Seq[Generator[Generator[T]]], maxCacheSize: Int): CachingGenerator[Type, T] =
    new GeneratorCachingGenerator[T](
      wrapppedGeneratorGenerators = generatorGenerators,
      maxCacheSize = maxCacheSize)
}


private class GeneratorCachingGenerator[Value](wrapppedGeneratorGenerators: Seq[Generator[Generator[Value]]], maxCacheSize: Int)
  extends AbstractCachingGenerator[Type, Value](identity, maxCacheSize) {


  private def findGeneratorGenerator(tc: TypeAndRandom): Option[Generator[Generator[Value]]] = {
    wrapppedGeneratorGenerators.find(_.isDefinedAt(tc))
  }

  override protected def canHandle(tc: TypeAndRandom): Boolean = findGeneratorGenerator(tc).isDefined

  override protected def findGenerator(tc: TypeAndRandom): Option[Generator[Value]] = findGeneratorGenerator(tc).map(_.apply(tc))

}


private class ByKeyLookupCachingGenerator[Key <: AnyRef, Value](generators: Seq[Generator[Value]], keyFromType: Type => Key, maxCacheSize: Int)
  extends AbstractCachingGenerator[Key, Value](keyFromType, maxCacheSize) {

  override protected def canHandle(tc: TypeAndRandom): Boolean = findGenerator(tc).isDefined

  override protected def findGenerator(tc: TypeAndRandom): Option[Generator[Value]] = generators.find(_.isDefinedAt(tc))

}

private abstract class AbstractCachingGenerator[Key <: AnyRef, Value](keyFromType: Type => Key, maxCacheSize: Int) extends CachingGenerator[Key, Value] {

  val cache: Cache[Key, Generator[Value]] = CacheBuilder.newBuilder()
    .maximumSize(maxCacheSize)
    .recordStats()
    .build[Key, Generator[Value]]()


  override def hasCached(tc: TypeAndRandom): Boolean = {
    getCached(tc).isDefined
  }

  override def isDefinedAt(tc: TypeAndRandom): Boolean = {
    hasCached(tc) || canHandle(tc)
  }

  private def getCached(tc: TypeAndRandom) = {
    Option(cache.getIfPresent(keyFromType(tc._1)))
  }

  protected def canHandle(tc: TypeAndRandom): Boolean

  protected def findGenerator(tc: TypeAndRandom): Option[Generator[Value]]

  override def apply(tc: TypeAndRandom): Value = {
    val cached = getCached(tc)
    if (cached.isDefined) {
      cached.get(tc)
    } else {
      val fn: Generator[Value] = findGenerator(tc).getOrElse(throw new MatchError(tc._1))
      cache.put(keyFromType(tc._1), fn)
      fn(tc)
    }
  }
}
