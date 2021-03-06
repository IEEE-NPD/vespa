// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/imported_attribute_vector_read_guard.h>
#include "i_tensor_attribute.h"

namespace search::attribute {
class ImportedAttributeVector;
}

namespace search::tensor {

/**
 * Short lived attribute vector for imported tensor attributes.
 *
 * Extra information for direct lid to target lid mapping with
 * boundary check is setup during construction.
 */
class ImportedTensorAttributeVectorReadGuard : public attribute::ImportedAttributeVectorReadGuard,
                                               public ITensorAttribute
{
    using ReferenceAttribute = attribute::ReferenceAttribute;
    using BitVectorSearchCache = attribute::BitVectorSearchCache;
    const ITensorAttribute &_target_tensor_attribute;
public:
    ImportedTensorAttributeVectorReadGuard(const attribute::ImportedAttributeVector &imported_attribute,
                                           bool stableEnumGuard);
    ~ImportedTensorAttributeVectorReadGuard();

    const ITensorAttribute *asTensorAttribute() const override;

    std::unique_ptr<vespalib::eval::Value> getTensor(uint32_t docId) const override;
    std::unique_ptr<vespalib::eval::Value> getEmptyTensor() const override;
    void extract_dense_view(uint32_t docid, vespalib::tensor::MutableDenseTensorView& tensor) const override;
    const vespalib::eval::Value& get_tensor_ref(uint32_t docid) const override;
    bool supports_extract_dense_view() const override { return _target_tensor_attribute.supports_extract_dense_view(); }
    bool supports_get_tensor_ref() const override { return _target_tensor_attribute.supports_get_tensor_ref(); }
    const vespalib::eval::ValueType &getTensorType() const override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;
};

}
