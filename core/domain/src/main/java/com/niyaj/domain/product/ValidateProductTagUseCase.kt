/*
 * Copyright 2024 Sk Niyaj Ali
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.niyaj.domain.product

import com.niyaj.common.result.ValidationResult
import com.niyaj.common.tags.ProductTestTags.PRODUCT_TAG_LENGTH_ERROR
import javax.inject.Inject

class ValidateProductTagUseCase @Inject constructor() {
    operator fun invoke(tagName: String): ValidationResult {
        if (tagName.isNotEmpty()) {
            if (tagName.length < 3) {
                return ValidationResult(
                    successful = false,
                    errorMessage = PRODUCT_TAG_LENGTH_ERROR,
                )
            }
        }

        return ValidationResult(true)
    }
}