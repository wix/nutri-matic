/*
 * Copyright 2018 Wix.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wix.nutrimatic.internal.generators

import com.wix.nutrimatic.Generators

private[nutrimatic] object Monads {
  val generators = Seq(
    Generators.byErasure[Option[_]]((t, r) =>
      if (r.randomBoolean) Some(r.makeComponent(t.typeArgs.head)) else None),
    Generators.byErasure[Either[_, _]]((t, r) =>
      if (r.randomBoolean) Left(r.makeComponent(t.typeArgs.head)) else Right(r.makeComponent(t.typeArgs.last)))
  )
}
