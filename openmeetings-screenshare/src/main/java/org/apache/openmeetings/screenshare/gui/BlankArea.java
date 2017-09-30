/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.screenshare.gui;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.SwingConstants;

public class BlankArea extends MouseListenerable {
	private static final long serialVersionUID = 1L;
	private static final Dimension MIN_SIZE = new Dimension(100, 50);

	public BlankArea(Color color) {
		setBackground(color);
		setOpaque(false);
		setHorizontalAlignment(SwingConstants.LEFT);
		setVerticalAlignment(SwingConstants.TOP);
		setHorizontalTextPosition(0);
		setVerticalTextPosition(0);
		setBorder(BorderFactory.createLineBorder(Color.black));
		setMinimumSize(MIN_SIZE);
		setPreferredSize(MIN_SIZE);
	}
}
