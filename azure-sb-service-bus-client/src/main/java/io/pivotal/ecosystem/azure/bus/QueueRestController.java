/**
 Copyright (C) 2017-Present Pivotal Software, Inc. All rights reserved.

 This program and the accompanying materials are made available under
 the terms of the under the Apache License, Version 2.0 (the "License”);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package io.pivotal.ecosystem.azure.bus;

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.CreateQueueResult;
import com.microsoft.windowsazure.services.servicebus.models.ListQueuesResult;
import com.microsoft.windowsazure.services.servicebus.models.QueueInfo;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMessageOptions;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMode;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveQueueMessageResult;

@RestController
public class QueueRestController
{
	private static final Logger LOG = LoggerFactory.getLogger(QueueRestController.class);
	private static final String CR = "</BR>";
	private static final String QUEUE_NAME = "PRODUCT_QUEUE";

	private final ServiceBusContract service;

	public QueueRestController(ServiceBusContract service) {
		this.service = service;
	}

	@RequestMapping(value = "/queue", method = RequestMethod.GET)
	public String process(HttpServletResponse response)
	{
		StringBuffer result = new StringBuffer();

		LOG.info("process start...");

		result.append("Connecting to service bus..." + CR);
		boolean queueExists = false;
		ListQueuesResult queueList;
		try
		{
			queueList = service.listQueues();
			for (QueueInfo queue:queueList.getItems())
			{
				result.append("Found queue " + queue.getPath() + CR);
				
				// Looks like Azure uses lower case for queue names so we need to ignore case
				if (QUEUE_NAME.equalsIgnoreCase(queue.getPath()))
				{
					queueExists = true;
					break;
				}
			}
			
			QueueInfo queueInfo = new QueueInfo(QUEUE_NAME);
			if (! queueExists)
			{
				try
				{
					result.append("Creating queue " + QUEUE_NAME + CR);
					CreateQueueResult queueResult = service.createQueue(queueInfo);
					result.append("Created queue " + queueResult.getValue().getPath() + CR);

				} catch (ServiceException e)
				{
					LOG.error("Error processing request ", e);
				}
			}
			else
			{
				result.append("Queue " + QUEUE_NAME + " already exists..." + CR);
			}
			
			result.append("Writing message to queue..." + CR);
			BrokeredMessage message = new BrokeredMessage("PCF is a great product!");
			service.sendQueueMessage(QUEUE_NAME, message);
			
		} catch (ServiceException e)
		{
			LOG.error("Error processing request ", e);
		}

		try
		{
			ReceiveMessageOptions opts = ReceiveMessageOptions.DEFAULT;
			opts.setReceiveMode(ReceiveMode.PEEK_LOCK);

			while (true)
			{
				result.append("Reading message from queue..." + CR);
				ReceiveQueueMessageResult resultQM = service.receiveQueueMessage(QUEUE_NAME, opts);
				BrokeredMessage message = resultQM.getValue();
				if (message != null && message.getMessageId() != null)
				{
					result.append("Read message from queue, id = " + message.getMessageId() + CR);

					List<String> contents = IOUtils.readLines(message.getBody());
					for (String s:contents)
					{
						result.append("Read message from queue, contents = " + s + CR);
					}
					
					result.append("Deleting message from queue..." + CR);
					service.deleteMessage(message);
				} 
				else
				{
					result.append("Finished reading messages from queue..." + CR);
					break;
				}
			}
		} catch (ServiceException e)
		{
			LOG.error("Error processing request ", e);
		} catch (Exception e)
		{
			LOG.error("Error processing request ", e);
		}

		result.append("Processed Date = " + new Date(System.currentTimeMillis()) + CR);

		LOG.info("QueueRestController process end");
		return result.toString();
	}

}
