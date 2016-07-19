/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.igov.model.analytic.attribute;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.persistence.Column;
import javax.persistence.FetchType;
import javax.persistence.OneToOne;
import org.igov.model.core.AbstractEntity;

/**
 *
 * @author olga
 */
@javax.persistence.Entity
public class Attribute_Float extends AbstractEntity{
    
    @JsonProperty(value = "nValue")
    @Column
    private Double nValue;
    
    @JsonProperty(value = "oAttribute")
    @OneToOne(fetch = FetchType.EAGER, mappedBy = "nID_Attribute")
    Attribute oAttribute;

    public Double getnValue() {
        return nValue;
    }

    public void setnValue(Double nValue) {
        this.nValue = nValue;
    }
    
    public Attribute getoAttribute() {
        return oAttribute;
    }

    public void setoAttribute(Attribute oAttribute) {
        this.oAttribute = oAttribute;
    }
    
}