package com.sun.corba.se.PortableActivationIDL;


/**
* com/sun/corba/se/PortableActivationIDL/NoSuchEndPoint.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from c:/jenkins/workspace/8-2-build-windows-x64-cygwin-sans-NAS/jdk8u431-mos/1361/corba/src/share/classes/com/sun/corba/se/PortableActivationIDL/activation.idl
* Monday, September 30, 2024 8:50:07 AM UTC
*/

public final class NoSuchEndPoint extends org.omg.CORBA.UserException
{

  public NoSuchEndPoint ()
  {
    super(NoSuchEndPointHelper.id());
  } // ctor


  public NoSuchEndPoint (String $reason)
  {
    super(NoSuchEndPointHelper.id() + "  " + $reason);
  } // ctor

} // class NoSuchEndPoint
