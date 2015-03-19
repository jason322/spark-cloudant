/*******************************************************************************
* Copyright (c) 2015 IBM Corp.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
package com.cloudant.spark

import org.apache.spark.sql.SQLContext
import play.api.libs.json.JsValue
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import play.api.libs.json.Json
import scala.util.control.Breaks._
import play.api.libs.json.JsUndefined
import java.net.URLEncoder
import com.cloudant.spark.common._

/**
 * @author yanglei
 */
@serializable case class CloudantConfig(val host: String, val dbName: String, val indexName: String = null)(implicit val username: String, val password: String, val partitions:Int, val maxInPartition: Int, val minInPartition:Int) extends JsonStoreConfig{
  
    private lazy val dbUrl = {"http://"+ host+"/"+dbName}

    val pkField = "_id"

    override def getPostUrl(): String ={dbUrl}
    
    def getOneUrl(): String = { dbUrl+ "/_all_docs?limit=1&include_docs=true"}
    
    def getRangeUrl(field: String = null, start: Any = null, startInclusive:Boolean = false, end:Any =null, endInclusive: Boolean =false,includeDoc: Boolean = true): (String, Boolean) = {
          val (url:String, pusheddown:Boolean) = calculate(field, start, startInclusive, end, endInclusive)
          if (includeDoc)
          {
            if (url.indexOf('?')>0) (url+"&include_docs=true",pusheddown)
            else (url+"?include_docs=true",pusheddown)
          }else
           ( url, pusheddown)
    }
    
    private def calculate(field: String, start: Any, startInclusive: Boolean, end:Any, endInclusive: Boolean): (String, Boolean) = {
      if (field!=null && field.equals(pkField))
      {
          var condition = ""
          if (start!=null && end!=null && start.equals(end))
          {
             condition += "?key=" + start 
          }else
          {
            if (start != null)
                condition += "?startkey=" + start 
            if (end !=null)
            {
                if (start !=null)
                  condition += "&"
                else
                  condition += "?"
                condition += "endkey=" + end
            }
          }
           (dbUrl + "/_all_docs"+URLEncoder.encode(condition,"UTF-8"), true)
      }else if (indexName!=null) //  push down to indexName
      {
          val condition = calculateCondition(field, start, startInclusive, end, endInclusive)
          (dbUrl+"/"+indexName+"?q="+condition, true)
      }else
        (dbUrl + "/_all_docs" ,false)
    }

    def getSubSetUrl (url: String, skip: Int, limit: Int) : String ={
      val suffix = "include_docs=true&limit="+limit+"&skip="+skip
      if (url.indexOf('?')>0) url+"&"+suffix
      else url+"?"+suffix
    }
    
    def getTotalRows(result: JsValue): JsValue = {
        result \ "total_rows"
    }
    
    def getRows(result: JsValue): Seq[JsValue] = {
        result \\ "doc"
    }
}