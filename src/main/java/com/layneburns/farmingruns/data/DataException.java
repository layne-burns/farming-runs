/*
 * Copyright (c) 2026, Layne Burns <https://github.com/layne-burns>
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License; see the LICENSE file in the project root.
 */
package com.layneburns.farmingruns.data;

/**
 * A data file is missing, malformed or refers to something that doesn't exist. The message names the file
 * and the entry, so the fix is a data edit (or re-running the script that generates the file).
 */
public class DataException extends RuntimeException
{
	public DataException(String message)
	{
		super(message);
	}

	public DataException(String message, Throwable cause)
	{
		super(message, cause);
	}
}
