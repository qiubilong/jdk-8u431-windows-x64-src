package org.omg.PortableInterceptor;


/**
* org/omg/PortableInterceptor/ObjectReferenceFactory.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from c:/jenkins/workspace/8-2-build-windows-x64-cygwin-sans-NAS/jdk8u431-mos/1361/corba/src/share/classes/org/omg/PortableInterceptor/Interceptors.idl
* Monday, September 30, 2024 8:50:07 AM UTC
*/


/** The object reference factory.  This provides the capability of
  * creating an object reference.
  */
public interface ObjectReferenceFactory extends org.omg.CORBA.portable.ValueBase
{

  /** make_object creates a CORBA object reference according
    * to the data in this template, with the given repository ID and
    * object ID.
    */
  public abstract org.omg.CORBA.Object make_object (String repositoryId, byte[] object_id);

} // interface ObjectReferenceFactory