/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/
package org.apache.olingo.odata2.core.batch.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.olingo.odata2.api.batch.BatchException;
import org.apache.olingo.odata2.api.batch.BatchParserResult;
import org.apache.olingo.odata2.api.client.batch.BatchSingleResponse;
import org.apache.olingo.odata2.api.uri.PathInfo;
import org.apache.olingo.odata2.core.batch.BatchHelper;
import org.apache.olingo.odata2.core.batch.BatchSingleResponseImpl;
import org.apache.olingo.odata2.core.batch.v2.BatchParserCommon.HeaderField;

public class BatchResponseTransformator implements BatchTransformator {

  private static final String REG_EX_STATUS_LINE = "(?:HTTP/[0-9]\\.[0-9])\\s([0-9]{3})\\s([\\S ]+)\\s*";

  public BatchResponseTransformator() {}

  @Override
  public List<BatchParserResult> transform(final BatchBodyPart bodyPart, final PathInfo pathInfo, final String baseUri)
      throws BatchException {
    return processQueryOperation(bodyPart, pathInfo, baseUri);
  }

  private List<BatchParserResult> processQueryOperation(final BatchBodyPart bodyPart,
      final PathInfo pathInfo,
      final String baseUri) throws BatchException {

    List<BatchParserResult> resultList = new ArrayList<BatchParserResult>();

    BatchTransformatorCommon.validateContentType(bodyPart.getHeaders());

    resultList.addAll(handleBodyPart(bodyPart));

    return resultList;
  }

  private List<BatchParserResult> handleBodyPart(final BatchBodyPart bodyPart) throws BatchException {
    List<BatchParserResult> bodyPartResult = new ArrayList<BatchParserResult>();

    if (bodyPart.isChangeSet()) {
      for (BatchQueryOperation operation : bodyPart.getRequests()) {
        bodyPartResult.add(transformChangeSet((BatchChangeSetPart) operation));
      }
    } else {
      bodyPartResult.add(transformQueryOperation(bodyPart.getRequests().get(0), getContentId(bodyPart.getHeaders())));
    }

    return bodyPartResult;
  }

  private BatchSingleResponse transformChangeSet(final BatchChangeSetPart changeSet) throws BatchException {
    BatchTransformatorCommon.validateContentTransferEncoding(changeSet.getHeaders(), true);

    return transformQueryOperation(changeSet.getRequest(), getContentId(changeSet.getHeaders()));
  }

  private BatchSingleResponse transformQueryOperation(final BatchQueryOperation operation, final String contentId)
      throws BatchException {
    BatchSingleResponseImpl response = new BatchSingleResponseImpl();
    response.setContentId(contentId);
    response.setHeaders(BatchParserCommon.headerFieldMapToSingleMap(operation.getHeaders()));
    response.setStatusCode(getStatusCode(operation.httpStatusLine));
    response.setStatusInfo(getStatusInfo(operation.getHttpStatusLine()));
    response.setBody(getBody(operation));

    return response;
  }

  private String getContentId(final Map<String, HeaderField> headers) {
    HeaderField contentIdField = headers.get(BatchHelper.HTTP_CONTENT_ID.toLowerCase(Locale.ENGLISH));

    if (contentIdField != null) {
      if (contentIdField.getValues().size() > 0) {
        return contentIdField.getValues().get(0);
      }
    }

    return null;
  }

  private String getBody(final BatchQueryOperation operation) throws BatchException {
    int contentLength = BatchTransformatorCommon.getContentLength(operation.getHeaders());

    return BatchParserCommon.trimStringListToStringLength(operation.getBody(), contentLength);
  }

  private String getStatusCode(final String httpMethod) throws BatchException {
    Pattern regexPattern = Pattern.compile(REG_EX_STATUS_LINE);
    Matcher matcher = regexPattern.matcher(httpMethod);

    if (matcher.find()) {
      return matcher.group(1);
    } else {
      throw new BatchException(BatchException.INVALID_STATUS_LINE);
    }
  }

  private String getStatusInfo(final String httpMethod) throws BatchException {
    Pattern regexPattern = Pattern.compile(REG_EX_STATUS_LINE);
    Matcher matcher = regexPattern.matcher(httpMethod);

    if (matcher.find()) {
      return matcher.group(2);
    } else {
      throw new BatchException(BatchException.INVALID_STATUS_LINE);
    }
  }

}
